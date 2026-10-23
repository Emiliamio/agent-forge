package com.agentforge;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AgentForge 核心架构分层与不可变契约守护测试")
@AnalyzeClasses(packages = "com.agentforge", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    /**
     * Rule 1: 控制器只能访问 Service，严禁直接访问 Mapper（零白名单、零例外）。
     */
    @ArchTest
    static final ArchRule controllers_should_not_access_mappers =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..")
                    .as("Rule 1: 控制器只能访问 Service，严禁直接访问 Mapper（零白名单零例外）")
                    .because("控制器严禁越过 Service 编排层直接操作数据库 Mapper，防止业务逻辑与事务控制在接入层泄露");

    /**
     * Rule 2: Service 层严禁反向依赖 Controller 层
     */
    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..")
                    .as("Rule 2: Service 业务逻辑层严禁反向依赖 Controller 接入层")
                    .because("Service 作为核心业务域，必须具备协议无关性，严禁反向耦合上层 HTTP 控制器或接入层实现");

    /**
     * Rule 3: 严禁在 main 代码中执行 DDL 或引用历史 schema.sql
     */
    @Test
    @DisplayName("Rule 3: 严禁在 main 源码中执行 DDL（无 CREATE/ALTER TABLE）或引用历史 schema.sql")
    void no_ddl_and_no_schema_sql_in_main_code() throws IOException {
        Path mainDir = Paths.get("src/main/java");
        if (!Files.exists(mainDir)) {
            mainDir = Paths.get("backend/src/main/java");
        }
        assertTrue(Files.exists(mainDir), "main source directory must exist");

        Pattern ddlPattern = Pattern.compile("(?i)(CREATE\\s+TABLE|ALTER\\s+TABLE|DROP\\s+TABLE|CREATE\\s+OR\\s+REPLACE\\s+FUNCTION)");
        try (var stream = Files.walk(mainDir)) {
            List<Path> offending = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.contains("schema.sql") || ddlPattern.matcher(content).find();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
            assertTrue(offending.isEmpty(), "Found prohibited DDL or schema.sql in main Java files: " + offending);
        }
    }

    /**
     * Rule 4 (R1): DTO 与 VO 严禁依赖 Mapper 与 Entity 持久层，保障严格单向分层 controller -> service -> mapper -> entity
     */
    @ArchTest
    static final ArchRule r1_dto_and_vo_should_not_depend_on_mappers_or_entities =
            noClasses().that().resideInAnyPackage("..dto..", "..vo..")
                    .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "..entity..")
                    .as("Rule 4 (R1): DTO 与 VO 严禁直接依赖 Mapper 与 Entity（数据契约与持久层解耦）")
                    .because("DTO 与 VO 是协议层数据契约，必须独立于底层数据库实体与持久层映射，防止领域模型穿透泄露并破坏严格单向分层架构");

    /**
     * Rule 5 (R2): 控制器严禁直接依赖 Entity 领域实体，入参与返回值只能使用 DTO/VO/基础类型
     */
    @ArchTest
    static final ArchRule r2_controllers_should_not_depend_on_entities =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..entity..")
                    .as("Rule 5 (R2): 控制器严禁依赖 Entity 领域实体（入参出参严格 DTO/VO 化）")
                    .because("控制器只能接收 DTO 入参并返回 VO/基础类型，严禁直接暴露 Entity 数据库实体（反例：@RequestBody AgentApp 或 Result<WorkflowDefinition>）");

    /**
     * Rule 6 (R3): @Transactional 事务注解只能在 service 包中使用，严禁在其他层声明事务
     */
    @ArchTest
    static final ArchRule r3_transactional_only_in_service =
            noClasses().that().resideOutsideOfPackage("..service..")
                    .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.transaction.annotation.Transactional")
                    .as("Rule 6 (R3): @Transactional 事务注解只能在 Service 业务逻辑层声明")
                    .because("事务生命周期边界必须严格收敛在 Service 业务层，严禁在 Controller 接入层或其它包声明事务，防止长事务死锁与连接池资源耗尽（反例：在 Controller 类或方法上标注 @Transactional）");

    /**
     * Rule 7 (R4): 包级无环形依赖
     */
    @ArchTest
    static final ArchRule r4_no_cycles =
            slices().matching("com.agentforge.(*)..").should().beFreeOfCycles()
                    .as("Rule 7 (R4): 包级无环形依赖")
                    .because("架构切片之间不得存在循环依赖，保障系统模块单向演进与独立可维护性");

    /**
     * Rule 8 (R4): 业务特性内部实现隔离 (双向：agent.internal 与 rag.internal 互不穿透)
     */
    @ArchTest
    static final ArchRule r8_cross_feature_internal_boundary =
            noClasses().that().resideInAPackage("..agent.internal..")
                    .should().dependOnClassesThat().resideInAPackage("..rag.internal..")
                    .as("Rule 8a: 业务特性实现隔离（agent.internal 禁止依赖 rag.internal）")
                    .because("业务特性模块之间必须保持单向接口依赖，跨模块只能依赖对外暴露的 api 契约，严禁穿透耦合 internal 内部实现");

    @ArchTest
    static final ArchRule r8b_rag_internal_must_not_depend_on_agent_internal =
            noClasses().that().resideInAPackage("..rag.internal..")
                    .should().dependOnClassesThat().resideInAPackage("..agent.internal..")
                    .as("Rule 8b: 业务特性实现隔离（rag.internal 禁止依赖 agent.internal）")
                    .because("双向约束才构成隔离：单向规则只能防止一个方向的穿透，反向耦合同样会破坏模块独立演进");

    /**
     * Rule 9 (R1-a): 持久层 Mapper 严禁反向依赖 Service 业务层
     */
    @ArchTest
    static final ArchRule r1_mapper_must_not_depend_on_service =
            noClasses().that().resideInAPackage("..mapper..")
                    .should().dependOnClassesThat().resideInAPackage("..service..")
                    .as("Rule 9 (R1-a): Mapper 持久层禁止依赖 Service 业务层")
                    .because("分层必须单向：持久层若反向依赖业务层，会形成 mapper -> service -> mapper 的闭环，导致持久层无法独立测试与复用（反例：Mapper 里注入 Service 做业务校验）");

    /**
     * Rule 10 (R1-b): 领域实体 Entity 严禁依赖 Service / Mapper
     */
    @ArchTest
    static final ArchRule r1_entity_must_not_depend_on_service_or_mapper =
            noClasses().that().resideInAPackage("..entity..")
                    .should().dependOnClassesThat().resideInAnyPackage("..service..", "..mapper..")
                    .as("Rule 10 (R1-b): Entity 领域实体禁止依赖 Service / Mapper")
                    .because("实体是贫血数据模型，必须保持与业务逻辑、持久层实现无关，否则无法跨场景复用且极易产生环形依赖（反例：Entity 里调用 Mapper 或 Service 做级联查询）");
}

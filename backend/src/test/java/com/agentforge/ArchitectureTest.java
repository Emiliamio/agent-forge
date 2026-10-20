package com.agentforge;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AgentForge 核心架构分层与不可变契约守护测试")
@AnalyzeClasses(packages = "com.agentforge", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    /**
     * Rule 1: 控制器只能访问 Service，严禁直接访问 Mapper。
     * 架构白名单说明:
     * - PublicAgentController 访问 AgentAppMapper: 免登公开挂件直接校验智能体公开状态，避免全量 Service 代理以保障极端时延性能；
     * - 存量 CRUD 控制器（AgentAppController, AuthController, BillingController, ChatSessionController, WorkflowController）:
     *   历史遗留单表 CRUD 直接访问对应 Mapper，待后续版本统一抽取 Service 门面。
     */
    @ArchTest
    static final ArchRule controllers_should_not_access_mappers =
            noClasses().that().resideInAPackage("..controller..")
                    .and().doNotHaveSimpleName("PublicAgentController")
                    .and().doNotHaveSimpleName("AgentAppController")
                    .and().doNotHaveSimpleName("AuthController")
                    .and().doNotHaveSimpleName("BillingController")
                    .and().doNotHaveSimpleName("ChatSessionController")
                    .and().doNotHaveSimpleName("WorkflowController")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..")
                    .as("控制器原则上只能访问 Service，严禁直接访问 Mapper（白名单：PublicAgentController 访问 AgentAppMapper；存量 CRUD 控制器已登记）");

    /**
     * Rule 1 补丁约束: PublicAgentController 仅允许且仅能访问 AgentAppMapper，严禁横向穿透访问其他 Mapper
     */
    @ArchTest
    static final ArchRule public_agent_controller_mapper_restriction =
            noClasses().that().haveSimpleName("PublicAgentController")
                    .should().dependOnClassesThat(
                            resideInMapperPackageAndNotAgentAppMapper()
                    )
                    .as("PublicAgentController 仅白名单放行 AgentAppMapper，严禁穿透访问其他业务持久层 Mapper");

    private static com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass> resideInMapperPackageAndNotAgentAppMapper() {
        return new com.tngtech.archunit.base.DescribedPredicate<>("reside in ..mapper.. but not AgentAppMapper") {
            @Override
            public boolean test(com.tngtech.archunit.core.domain.JavaClass javaClass) {
                return javaClass.getPackageName().contains(".mapper") && !"AgentAppMapper".equals(javaClass.getSimpleName());
            }
        };
    }

    /**
     * Rule 2: Service 层严禁反向依赖 Controller 层
     */
    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..")
                    .as("Service 业务逻辑层严禁反向依赖 Controller 接入层");

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
}

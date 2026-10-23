# ADR 0006: 六大架构不变量与机器可验证防御体系 (Architecture Invariants R1-R6)

## 状态
已接受 (Accepted) - 2026-10-23

## 背景
随着 AgentForge 演进至企业级商业发行版（v1.2+），系统已包含多租户智能体编排、三路混合 RAG、动态 OpenAPI 工具生态与计费网关等复杂子系统。
单纯依赖开发人员的人工代码审查（Code Review）难以防御偶发的架构退化：
- 控制器容易因开发便利直接注入 Mapper 越权查库，或直接使用 Entity 导致 Mass Assignment 安全漏洞；
- 事务注解滥用可能蔓延至 Web 接入层引发连接池枯竭；
- 包切片间缺乏环路检测容易形成网状死锁；
- 外部大模型与存储依赖缺乏端口隔离导致测试脆弱断网瘫痪；
- 接口签名变更缺乏契约防漂移快照机制。

为了将系统架构健康度提升至无可挑剔的 **10/10 工业级基线**，必须确立机器可验证的架构六大不变量（Invariants R1–R6），并全量集成至自动化构建与 CI 门禁。

## 决策内容

确立以下六大架构不变量，全部通过 ArchUnit、OpenAPI Snapshot 与测试套件由机器强制核验，禁止任何白名单例外或豁免注解：

### 1. R1: 单向分层调用契约 (Layering Hierarchy)
- **规则**：系统分层必须严格遵循 `controller -> service -> mapper -> entity` 单向调用链；
- **防线**：`controller`、`dto`、`vo` 严禁依赖 `mapper`；`mapper` 严禁反向依赖 `service`；`entity` 严禁依赖 `service` 或 `mapper`；
- **机器门禁**：`ArchitectureTest` 规则 1, 2, 3。

### 2. R2: 控制器入参出参严格 DTO/VO 化 (Controller DTO/VO Boundary)
- **规则**：所有控制器方法只能接收 DTO 入参，只能返回 VO/基础数据类型，严禁直接暴露 Entity 数据库持久化实体；
- **防线**：控制层消除全部 `import com.agentforge.entity.*`；通过实体转换器（`*Converter`）执行纯内存字段映射；
- **机器门禁**：`ArchitectureTest` 规则 4, 5。

### 3. R3: 事务注解严格收敛在服务层 (Transactional Boundary)
- **规则**：`@Transactional` 注解只能在 `com.agentforge.service..` 包中使用，严禁在控制器、持久层或配置类声明事务；
- **防线**：杜绝长事务占用数据库物理连接，避免 Web 接入层发生 HTTP I/O 时引发连接池耗尽雪崩；
- **机器门禁**：`ArchitectureTest` 规则 6。

### 4. R4: 包级架构切片无环与特性隔离 (Cycle-Free Architecture Slices)
- **规则**：`com.agentforge.(*)..` 包级切片之间必须严格保持有向无环（DAG）；跨业务特性模块（`agent`、`rag` 等）内部实现（`internal`）禁止直接穿透依赖；
- **防线**：通过移动公共 TypeHandler 至 `entity.handler`、解耦 `Result` 与 `ErrorCode`，消除历史环状依赖；
- **机器门禁**：`ArchitectureTest` 规则 7, 8。

### 5. R5: OpenAPI 契约快照与防漂移回归 (OpenAPI Contract Snapshot)
- **规则**：Springdoc 生成的 `/v3/api-docs` 必须与标准基线 `docs/openapi.json` 保持 100% 字符级规范化一致；
- **防线**：任何未授权的路径、HTTP 方法、参数增删或模型结构变更，均会被 CI 契约测试直接阻断并输出精准差异（Diff）；
- **机器门禁**：`OpenApiContractTest`。

### 6. R6: 外部重依赖端口隔离与 Fake 闭环 (Hexagonal Ports & Fakes)
- **规则**：大模型对话、向量嵌入与文件存储三大重依赖必须抽象为端口接口（`ChatModelPort`, `EmbeddingPort`, `DocumentStoragePort`）；
- **防线**：在 `src/test` 建立纯内存、零依赖的确定性测试替身（`Fake*`），并由集成测试真实注入口驱动闭环，保障测试在完全离线环境下 100% 极速稳定运行；
- **机器门禁**：`HexagonalPortsAndFakesTest` + Fake 类存在性断言。

## 影响评估
- **工程鲁棒性**：任何违背分层契约的代码在本地 `mvn clean verify` 阶段就会立即抛出断言错误，阻断提交；
- **安全与合规**：彻底杜绝 Mass Assignment 批量赋值攻击与敏感字段泄露，符合 OWASP Top 10 安全规范；
- **CI 效能与确定性**：无公网依赖与 Flaky 测试，所有单元测试与契约测试稳定在毫秒级完成。

## 实现现状与已知边界（2026-10-23 审计后补记）

| 项 | 状态 | 说明 |
|---|---|---|
| R1 三条规则 | ✅ 全部落地 | `controllers_should_not_access_mappers`、`r1_mapper_must_not_depend_on_service`、`r1_entity_must_not_depend_on_service_or_mapper`；`ArchitectureTest` 共 **12 项**（11 条 ArchUnit 规则 + 1 个 DDL 扫描测试） |
| R2 | ✅ | `r2_controllers_should_not_depend_on_entities`，基于字节码依赖，全限定名（FQN）写法同样会被拦下（审计已注入 FQN 探针验证） |
| R3 | ✅ | `r3_transactional_only_in_service` |
| R4 无环 | ✅ | `r4_no_cycles`（`slices().matching("com.agentforge.(*)..")`） |
| R4 特性隔离 | ✅ 8 个域全覆盖 | 已落地 8 个业务特性域全覆盖（`agent` / `rag` / `billing` / `security` / `platform` / `workflow` / `chat` / `market`，各含 `api` 与 `internal` 两层）；通用 Rule 11 切片规则（`slices().matching("com.agentforge.service.(*).internal..")`）杜绝跨特性 internal 耦合；`service/converter` 属于跨特性共享映射层（不属于单一特性域） |
| R5 | ✅ 且实测有效 | 审计时注入 `/health-probe` 端点 → 契约测试立即红（`Failures: 1`），回滚后转绿 |
| R6 | ✅ | 3 端口 + 3 Fake（`test/java/com/agentforge/fake/`），由 `HexagonalPortsAndFakesTest` 注入使用 |

**不要用 `-Dtest=ArchitectureTest#某方法名` 精确选择规则**：`@ArchTest` 是静态字段而非测试方法，
surefire 无法按方法过滤，会退化成"跑整个类"——看起来通过，实际没有选中那条规则。统一用 `-Dtest=ArchitectureTest test`。

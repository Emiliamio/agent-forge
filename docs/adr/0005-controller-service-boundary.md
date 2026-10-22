# ADR 0005: 控制器-持久层边界技术债彻底偿还与零例外架构守护

## 状态
已接受 (Accepted) - 2026-10-22

## 背景
在 ADR 0004 中，由于历史遗留单表 CRUD 与挂件低时延诉求，6 个 Controller（`AuthController`、`AgentAppController`、`BillingController`、`ChatSessionController`、`WorkflowController`、`PublicAgentController`）在 `ArchitectureTest` 中被列为白名单例外直接访问持久层 Mapper。

为了将架构健康度冲至 10/10 工业级基线，消除潜在的数据访问扩散风险与架构规范松动，必须彻底消除所有白名单例外，实现**控制器到持久层零例外穿透隔离**。

## 决策内容
1. **抽取与拓展门面服务（Service Facades）**：
   - `AgentAppService` & `AgentAppServiceImpl`：接管智能体 CRUD（`createAgent`, `listAgents`, `getAgent`）及跨租户免登挂件定位（`findPublicApp`）；
   - `AuthQueryService` & `AuthQueryServiceImpl`：接管登录与用户信息所需的租户与用户查询（`findTenantByCode`, `findTenantById`, `findUserByTenantAndUsername`, `findUserById`）；
   - `BillingService` & `BillingServiceImpl`：拓展计量计费看板统计（`getBillingStats`）、消耗日志分页（`listUsageLogs`）及钱包充值（`recharge`）；
   - `ChatSessionService` & `ChatSessionServiceImpl`：拓展用户会话列表查询（`listUserSessions`）；
   - `WorkflowService` & `WorkflowServiceImpl`：接管可视化 DAG 工作流定义保存、详情查询与调度执行（`saveWorkflow`, `getWorkflow`, `runWorkflow`）。

2. **控制器全面解除持久层依赖**：
   - 彻底移除 6 个 Controller 中的所有 `import com.agentforge.mapper.*`；
   - 所有 Controller 统一注入并依赖对应的业务 Service 接口门面。

3. **收紧 ArchUnit 架构门禁**：
   - `ArchitectureTest` Rule 1 移除全部 `doNotHaveSimpleName` 白名单豁免；
   - 规则强制断言：任何位于 `..controller..` 的类严禁依赖 `..mapper..`，达成**零例外、零容忍**物理级分层契约。

## 影响评估
- **分层纯洁度**：Controller 层彻底回归接入层与请求路由职责，持久层 Mapper 仅对 Service 层可见；
- **可测试性提升**：单元测试与 Mock 粒度清晰稳定，不再需要 Mock 底层 MyBatis-Plus Mapper；
- **CI 持续防退化**：`ArchitectureTest` 在本地与 GitHub Actions CI 中自动化运行，后续新增任何穿透代码将直接阻断构建。

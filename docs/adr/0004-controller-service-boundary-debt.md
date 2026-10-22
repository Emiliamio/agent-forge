# ADR 0004: 控制器-持久层边界白名单与技术债治理

## 状态
已偿还（见 0005）- 2026-10-22

## 背景与现状
为保障系统分层整洁，`ArchitectureTest` Rule 1 确立了「Controller 只能访问 Service，严禁穿透访问 Mapper」的架构铁律。当前架构测试针对历史存量放行了 6 个 Controller 直连 Mapper：
- 历史单表 CRUD 未抽 Service 门面：`AuthController`、`AgentAppController`、`BillingController`、`ChatSessionController`、`WorkflowController`；
- 公开挂件极致时延诉求：`PublicAgentController` 为免除多层代理开销直连 `AgentAppMapper`。

## 决策内容
1. **冻结白名单范围**：将上述 6 个 Controller 作为明确的技术债显式登记，**新增 Controller 严禁再进入白名单**；
2. **细粒度穿透隔离**：通过 Rule 1 补丁规则物理锁死 `PublicAgentController` 仅允许访问 `AgentAppMapper`，严禁横向穿透其他持久层接口；
3. **分阶段抽取解耦**：后续版本统一重构抽取 Service 门面并逐步移除白名单豁免。

## 技术债清单与后续计划
| Controller | 当前直连 Mapper | 建议抽取的 Service 门面 | 重构里程碑 |
| :--- | :--- | :--- | :--- |
| `AuthController` | `SysUserMapper` | `SysUserService` | C 批迭代 (已于 D 批通过 AuthQueryService 偿还) |
| `AgentAppController` | `AgentAppMapper` | `AgentAppService` | C 批迭代 (已于 D 批偿还) |
| `BillingController` | `BillingRecordMapper` | `BillingService` | C 批迭代 (已于 D 批偿还) |
| `ChatSessionController` | `ChatSessionMapper`, `ChatMessageMapper` | `ChatSessionService` | C 批迭代 (已于 D 批偿还) |
| `WorkflowController` | `WorkflowMapper` | `WorkflowService` | C 批迭代 (已于 D 批偿还) |
| `PublicAgentController` | `AgentAppMapper` | `PublicAgentAppService` (纳管缓存) | C 批迭代 (已于 D 批通过 AgentAppService.findPublicApp 偿还) |

## 影响评估
- **收益**：既有分层守护规则立即生效，杜绝架构腐化蔓延；公开挂件在保障隔离约束的同时维持低时延；
- **代价**：存量代码存在轻量分层违背，需在后续版本完成 6 个控制器门面抽取（已在 ADR 0005 彻底偿清）。

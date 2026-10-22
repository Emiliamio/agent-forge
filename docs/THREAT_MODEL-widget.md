# AgentForge 公开挂件入口威胁模型分析 (Widget Threat Model)

> **评估目标**：面向全网免密公开访问的挂件交互入口（`POST /api/public/agents/{id}/chat` 及静态 SDK 托管）  
> **建模方法**：STRIDE 六维度模型（身份仿冒、数据篡改、抵赖、信息泄露、拒绝服务、权限提升）

---

## 1. STRIDE 挂件入口安全威胁分析矩阵

| STRIDE 分类 | 核心安全威胁 (Threat) | 既有防护与缓解措施 (Mitigation) | 对应验证测试用例 (Test Gate) | 残余风险与防护建议 (Residual Risk) |
| :--- | :--- | :--- | :--- | :--- |
| **S - 身份仿冒 (Spoofing)** | 伪造凭据或跨智能体/租户调用挂件，窃取或冒用企业客服配额 | v1 规范签名令牌 `HMAC-SHA256` 强绑定 `agentId\|tenantId`；验签通过前不记 Nonce；支持双阶段平滑轮换 | `SignedWidgetTokenTest#tamperedSignatureRejected`<br>`crossAgentTokenRejected`<br>`secretRotationSupport` | 客户端前端明文泄露持久 Secret；建议客户生成短期动态签名令牌 |
| **T - 数据篡改 (Tampering)** | 中间人篡改提问消息、伪造未申报参数扩张攻击面、恶意增大迭代轮次 | JSR-303 `@NotBlank` 校验，未申报参数一律拦截报错；服务端强校验截断 `maxIterations` ≤ 5 | `PublicAgentSecurityTest#testPromptAliasRejectedAndThrowsParamError` | 传输未启用 TLS；生产环境强制入口全链路 HTTPS 加密 |
| **R - 抵赖 (Repudiation)** | 恶意调用方否认高频恶意提问行为，逃避审计溯源 | 日志统一记录 Client IP、租户 ID、Agent ID 与错误拦截码；基于受信反代头解析真实 IP | `PublicAgentSecurityTest#testPublicDisabledThrowsForbidden` | 攻击方通过大规模公共代理池轮换 IP 逃避单 IP 溯源与封禁 |
| **I - 信息泄露 (Info Disclosure)** | 探测存活 Agent 拓扑、探针刺探数据库内部堆栈、通过校验时序侧信道推测密钥 | 统一友好报错脱敏；未启用挂件一律统一 403；Token 比对全量使用 `MessageDigest.isEqual` 常量时间比对 | `PublicAgentSecurityTest#testPublicTokenRequiredMismatchThrowsUnauthorized` | 智能体关联知识库内容过于宽泛；建议客户配置敏感词拦截与 RAG 知识脱敏 |
| **D - 拒绝服务 (Denial of Service)** | 高频流量打爆系统资源、重放已拦截合法令牌、伪造 Nonce 击穿防重放窗口 | 三重原子 Lua 限流 (IP 30 RPM, Agent 300 RPM, Token 60 RPM)；带动态 `Retry-After`；`ReplayGuard` 防重放；prod 模式下 Redis 故障 Fail-Closed 熔断 | `RedisDownFailClosedLiveTest#testRedisDownFailClosedVsFailOpen`<br>`RateLimitRetryAfterLiveTest#testRateLimit429Headers` | 分布式千兆级别极端洪泛 DDoS；需在宿主边缘层配置 Cloudflare/阿里云 WAF |
| **E - 权限提升 (Elevation of Privilege)** | 试图绕过挂件隔离越权读取管理端 API 或穿透操作其他租户知识库 | 挂件路由独立隔离于 `/api/public/**`，严格校验 `public_enabled == 1`；对话执行强制动态注入并受控隔离租户上下文 | `AuthzMatrixTest#testPublicWidgetAccessControl`<br>`PublicAgentSecurityTest#testPublicDisabledThrowsForbidden` | 客户误为公开智能体挂载高权限写操作工具；建议仅挂载只读 RAG 与受控沙箱工具 |

---

## 2. 威胁收敛与常态化安全门禁

1. **发布前全量门禁覆盖**：上述 6 维威胁项全部由自动化测试（单元测试、安全切面测试、分布式容器集成测试）覆盖，在 CI 门禁中 100% 阻断回归；
2. **密钥轮换治理**：支持双阶段密钥轮换机制（`public_token_prev`），单次轮换过渡窗口建议 ≤ 24 小时；
3. **降级策略透明可控**：通过 `agentforge.security.fail-open-when-redis-down` 与 `agentforge.widget.legacy-token-enabled` 双配置硬开关，实现生产安全性与开发可用性的解耦控制。

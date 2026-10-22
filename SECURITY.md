# 安全策略

## 支持版本

| 版本 | 安全更新 |
| :--- | :--- |
| 1.x（当前主线） | ✅ 支持 |
| < 1.0 预览版 | ❌ 不再支持 |

## 挂件令牌安全规范与废弃计划

### v1 签名令牌规范
公开免密挂件 (`POST /api/public/agents/{id}/chat`) 采用严格防重放、带过期时间且与智能体/租户强绑定的签名令牌：
- **结构**：`v1.<exp>.<nonce>.<hmacBase64Url>`
- **签名算法**：`HMAC-SHA256(secretKey, agentId + "|" + tenantId + "|" + exp + "|" + nonce)` 采用 RFC 4648 Base64Url 无填充编码
- **绑定关系**：签名内容严格绑定 `agentId` 与 `tenantId`，跨智能体伪造令牌将被立即拒绝（返回 HTTP 401 签名校验失败）；Secret 轮换会导致历史已签发旧令牌立即失效
- **验签顺序保障**：服务端严格先执行 HMAC-SHA256 常量时间签名校验，验签通过后才记录 Nonce，杜绝未签名伪造请求消耗/烧毁 Nonce 防重放窗口
- **兼容说明**：v1.0.0 尚未对外正式发布，格式演进无历史兼容负担；存量明文 token 降级兼容分支保持不变
- **防重放**：`ReplayGuard` 基于 Redis 分布式 Nonce 记录（TTL 为过期时间 + 300 秒），Redis 不可用时无缝降级为本地内存 LRU 缓存，保障零阻断高可用
- **时钟宽限**：允许 30 秒时钟漂移宽限期

### 废弃计划 (Deprecation Policy)
- **v1.0.0+**：正式引入 v1 签名令牌，存量明文 token 保留向后兼容支持（服务端记录 WARN 告警提示升级并防时序攻击）；
- **v1.1.0+**：引入 `agentforge.widget.legacy-token-enabled` 安全硬开关（支持一键全局强制下线明文令牌）；
- **v1.2.0**：计划全面废弃明文 token 支持，未签名的明文 token 将直接返回 HTTP 401 Unauthorized。

### 防重放与限流高可用策略 (Fail-Open vs Fail-Closed)

挂件限流与 Nonce 防重放依赖分布式 Redis。系统通过配置 `agentforge.security.fail-open-when-redis-down` 显式声明 Redis 故障时的行为基线：
- **开发与单机测试环境 (dev)**：配置为 `true`（Fail-Open 默认降级），Redis 宕机或未部署时平滑降级为本地内存 LRU 缓存与滑动计数器，保障单机体验与开发零阻断；
- **生产与高可用集群环境 (prod)**：在 `application-prod.yml` 中显式设为 `false`（Fail-Closed 默认拒绝），当 Redis 不可用时，坚决拦截挂件请求并返回 **HTTP 503 Service Unavailable + `Retry-After: 5`**，杜绝在多实例集群下因内存降级导致分布式限流与防重放机制被击穿绕过。

### Nginx 安全响应头与挂件跨域公开例外

- **全局安全加固**：前端 Nginx 默认注入 5 大严苛安全响应头：`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer`、`Permissions-Policy` 以及针对 Vue/Vite 工业级 SPA 调优的 `Content-Security-Policy`；
- **挂件 SDK 公开跨域例外**：针对 `location = /agentforge-widget.js` 单独配置 `Access-Control-Allow-Origin: *` 是**经过审慎安全评估的有意例外**，因该脚本作为开放 SDK，必须允许任意第三方业务系统与外部买家站点无障碍跨域嵌入加载。

### 挂件密钥平滑轮换操作手册 (Secret Rotation Runbook)

针对生产环境可能发生的安全泄露或定期密钥安全轮换需求，AgentForge 通过数据库字段 `public_token_prev`（由 Flyway V6 引入）支持**双阶段平滑轮换**，保障轮换过程中现有客户端无感知、零停机：

1. **生成新密钥与轮换 SQL**：
   运行轮换辅助脚本：
   ```bash
   python scripts/rotate_widget_secret.py --agent-id <AGENT_ID> --tenant-id <TENANT_ID>
   ```
2. **第一阶段：启动轮换过渡窗口（建议窗口时间 ≤ 24 小时）**：
   在数据库中执行脚本输出的更新语句，将原密钥转移至 `public_token_prev`，并写入新密钥至 `public_token`。
   在轮换过渡窗口内，**使用新旧密钥签发的令牌均可正常访问（HTTP 200）**。
3. **第二阶段：收敛并关闭过渡窗口**：
   在客户端完成新令牌或新挂件代码部署后（或 24 小时窗口期满后），执行收敛 SQL 清空 `public_token_prev`：
   ```sql
   UPDATE agent_app SET public_token_prev = NULL WHERE id = <AGENT_ID> AND tenant_id = <TENANT_ID>;
   ```
   旧密钥彻底失效，任何残留旧令牌将被安全拦截（HTTP 401）。
4. **应急回滚**：若轮换过程中发现客户端异常，可执行回滚 SQL 恢复旧密钥。

## 报告漏洞

请发送邮件至 **mio2110767128@163.com**，主题以 `[SECURITY]` 开头，内容包含：

1. 受影响版本 / commit sha；
2. 复现步骤与最小 PoC（不要附带真实用户数据）；
3. 影响评估（数据泄漏 / 权限提升 / RCE / 拒绝服务）；
4. 是否已公开披露（若已公开，请注明时间与渠道）。

**请勿**在公开 Issue 或 PR 中披露未修复漏洞。

## 我们的承诺

- **72 小时内**确认收到并给出初步评估；
- 修复后在下一次发布说明中致谢（除非你要求匿名）；
- 不对善意研究追究责任，但禁止：越界测试非自有系统、破坏数据、影响服务可用性。

## 出范围

- 依赖组件的已知 CVE（请直接向上游报告，我们会在发布前合并修复）；
- 需要物理接触或已取得管理员权限的攻击场景；
- 社会工程与钓鱼测试。

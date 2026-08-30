# 🛡️ AgentForge 企业级高可用容灾与应急预案 SOP

**版本**：v1.0.0 Enterprise SRE / DevOps Standard  
**指标要求**：RTO < 30 秒（服务恢复时间）· RPO = 0（数据零丢失）· 核心服务可用性 99.99%

---

## ⚡ 一、 核心故障场景与自动自愈矩阵

| 故障场景 | 影响级别 | 系统自动防御与自愈措施 | 运维人工介入方案 |
| :--- | :---: | :--- | :--- |
| **主模型 API Key 欠费或限流 (429)** | P2 | `ResilientLlmFailoverService` 毫秒级自动切换备用 Key；若全量欠费，平滑降级至本地内网 Ollama 离线集群。 | 登录运营后台充值 API Key 额度。 |
| **Redis 缓存节点宕机** | P3 | 语义缓存自动短路降级为直连数据库检索，系统服务不中断，保障问答正常响应。 | 启动 Redis 哨兵或从节点接管。 |
| **客户客户端网络瞬断 (SSE 断连)** | P4 | `SseDisconnectGuard` 实时捕获取消信号，立即中断下游大模型调用与 Token 计费，防止算力空耗。 | 无需人工干预。 |
| **高并发流量突增击穿 (DDoS/大促)** | P1 | 基于 Redis 分布式令牌桶算法执行租户级 RPM/TPM 限流，溢出请求进入排队缓冲池。 | 弹性横向扩容 Java 21 后端节点。 |

---

## 🔄 二、 PostgreSQL 数据库主从切换与冷备恢复流程

1. **一键执行数据库冷备**：
   ```bash
   chmod +x scripts/backup_database.sh && ./scripts/backup_database.sh
   ```
2. **故障应急恢复**：
   ```bash
   cat backups/YYYYMMDD_HHMMSS/agent_forge_dump.sql | docker exec -i agentforge-postgres psql -U postgres -d agent_forge
   ```
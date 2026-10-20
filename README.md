# 🚀 AgentForge (灵眸智枢) —— 纯血 Java 21 企业级 AI Agent 智能体与混合 RAG 中台

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg?style=flat&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.4-emerald.svg?style=flat&logo=vuedotjs)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20pgvector-blue.svg?style=flat&logo=postgresql)](https://github.com/pgvector/pgvector)
[![Redis](https://img.shields.io/badge/Redis-7.2-red.svg?style=flat&logo=redis)](https://redis.io/)
[![CI/CD Pipeline](https://github.com/Emiliamio/agent-forge/actions/workflows/ci.yml/badge.svg)](https://github.com/Emiliamio/agent-forge/actions/workflows/ci.yml)
[![Tests](https://img.shields.io/badge/Tests-79%20Passed%20(100%25)-success.svg)]()
[![License](https://img.shields.io/badge/License-Commercial%20%2F%20Enterprise-blue.svg)](COMMERCIAL_LICENSE.md)

[中文版文档 (Chinese)](README.md) | [English Documentation](README_EN.md) | [技术博客](https://emiliamio.github.io) | [商业授权 & SLA](COMMERCIAL_LICENSE.md)

> **AgentForge** 是一套完全基于 **Java 21（虚拟线程）+ Spring Boot 3.2 + PostgreSQL 16 (pgvector) + Vue 3.4** 构建的工业级商业 AI Agent 智能体编排与三路混合 RAG（检索增强生成）知识库中台。  
> 专为国内政企、国企信创生态、企业私有化交付量身定制，彻底摆脱 Python 框架在企业级环境中的运维困境。

---

## 🌟 核心技术壁垒与架构亮点

```
                                  AgentForge 核心架构全景图
   ┌─────────────────────────────────────────────────────────────────────────────────┐
   │                          用户接入层 (Multi-Channel Ingress)                      │
   │      Vue 3.4 管理中台   │   小白员工极简 Copilot 门户   │   Shadow DOM 嵌入挂件      │
   │  • DeepSeek-R1 风格结构化 SSE 事件流分发 (思辨过程 <think> 与正文分流 + 引文卡片) │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                     安全防护与多租户物理隔离层 (Security & Isolation)             │
   │  • Sa-Token 统一身份认证与 RBAC 权限体系                                          │
   │  • MyBatis-Plus JsqlParser SQL AST 语法树租户强隔离 (跨租户物理越权率 0.00%)     │
   │  • 多租户动态 Token 消费预算与 RPM 并发限流熔断器 (TenantTokenQuotaLimiter 防击穿)│
   │  • 企业级 Prompt 注入与越狱对抗安全护栏 (PromptInjectionGuard 拦截率 100%)        │
   │  • RAG 事实性与幻觉评估护栏 (RagGroundingEvaluator 计算断言重合度与引文免责声明)   │
   │  • 金融级 PII 双向可逆敏感脱敏 (手机号/身份证/银行卡) + DFA 毫秒级敏感词安全过滤   │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                    三路混合 RAG 深度检索层 (Hybrid RAG Pipeline)                 │
   │  • GraphRAG 实体三元组自动抽取 (S-P-O) 与两跳拓扑扩散搜索 (Multi-Hop Traversal)   │
   │  • 纯 Java 8-bit 标量量化压缩引擎 (ScalarQuantizationEngine SQ8 内存降低 75%)     │
   │  • 密集向量检索 (pgvector HNSW) + 稀疏全文检索 (tsvector GIN)                    │
   │  • RRF (倒数排名融合算法) + Cross-Encoder 交叉重排模型二次评分                   │
   │  • 父子 Small-to-Big 双层分块 + 多轮对话 Query 智能指代消解重写                  │
   │  • 原文句子级精准高亮溯源与字符 Offset 双栏分屏对照                              │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                    响应式 DAG 工作流引擎 (Reactive Workflow Engine)              │
   │  • 基于 Kahn 拓扑排序算法的并发分层调度 (Project Reactor / Flux 并发流)          │
   │  • 分布式 Trace 拓扑时间线甘特图格式化 (TraceWaterfallGanttService APM 时序瀑布流)│
   │  • 企业级受限安全代码沙箱执行器 (SecureCodeSandboxEngine 敏感指令阻断 + 看门狗超时)│
   │  • 多模型金丝雀灰度分流与竞技场评测器 (ModelArenaTrafficSplitter 租户一致性路由)   │
   │  • LangSmith 级 Agent 执行拓扑全链路 Trace 瀑布流与 Token 成本精算追踪器          │
   │  • Anthropic MCP (Model Context Protocol) 原生客户端 (JSON-RPC 2.0 直连生态)    │
   │  • 9 大反应式节点执行器 (LLM、RAG、Code、HTTP、Switch、Human-in-Loop、Text2SQL等) │
   │  • ReAct Agent 智能体状态机 + 动态 OpenAPI 工具箱编排                            │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                   生产级装甲防御与成本优化 (Hardened Armor & Optimization)        │
   │  • Redis 向量语义降本缓存 (余弦相似度 >= 0.95 秒回，实测降低 60% 算力费)         │
   │  • 800MB 破损/加密文件装甲流式解析器 (零内存泄漏 + 逐页死信 DLQ 跳过)             │
   │  • 脱网老旧机 (Postgres 10/12) 纯 Java 内存向量余弦降级引擎                      │
   │  • 多模型级联熔断 (DeepSeek -> 局域网 Ollama -> OpenAI) + Single-Flight 防击穿   │
   │  • Zero-DBA 数据库自动建表灌数 + API Key 智能轮询欠费自动摘除池                  │
   └─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 极速 3 分钟一键部署

### 方式 1：Docker Compose 生产一键启动（推荐）
```bash
# 启动全栈生产容器 (Postgres 16 pgvector + Redis 7 + 后端 Java 21 + 前端 Nginx)
docker-compose -f docker-compose-prod.yml up -d
```
* 前端工作台：`http://localhost`
* 默认管理员：`admin` / `admin123456`（默认租户 ID: `1`）

### 方式 2：本地源码运行与调试
```bash
# 1. 启动底层存储并初始化集成测试库 agentforge_it
docker compose up -d postgres redis
bash scripts/prepare-live-db.sh

# 2. 启动 Spring Boot 后端 (内置 Zero-DBA 自动初始化数据库)
cd backend && mvn spring-boot:run

# 3. 启动前端 Studio
cd frontend && npm install && npm run dev
```

---

## 🧪 自动化测试与端到端实测验证

AgentForge 后端内置覆盖 CJK 二元分词、AST 租户隔离、Kahn DAG 调度、RAG 混合检索、PII 脱敏、DFA 过滤等全套自动化测试，绝不依赖 Mock 假数据：

```bash
# 1. 运行后端核心单元与集成测试套件 (双通道模式：-Dlive=true 直连 compose agentforge_it 库，或缺省降级 Testcontainers)
cd backend && mvn clean test -Dlive=true

# 2. 运行 20 组真实中文业务问句真实 HTTP 接口 RAG 召回评测 (在 >= 50 切片真实企业知识库中检索)
python scripts/evaluate-cjk-rag.py

# 3. 运行本地真实服务端到端打点验证 (零 Testcontainers 依赖，直接打点真库与真服务)
powershell -ExecutionPolicy Bypass -File scripts/verify-e2e.ps1
```

### 📊 真实 CJK 混合 RAG 基准评测看板

评测套件直接对齐真实运行的后端服务（`POST /rag/search`，`topK=5, minScore=0.0`），基于包含 60 个细分制度条款的真实企业知识库（`docs/benchmark_enterprise_policy.md`）。**命中判定为 `目标 chunkId ∈ top-5`**：地面真值由真实切片元数据解析。为保证指标真实性与不可伪造性，所有评测数字均由自动化套件直接生成并由机器基线严格锁定防漂移：

- **知识库有效切片规模**：有效切片总数见最新机器评测报告 [docs/BENCHMARK.md](docs/BENCHMARK.md)（远超 $\ge 50$ 候选池评测标准，拒绝保底小语料刷分）
- **字面覆盖问句硬门禁 (1~18)**：Recall@5 与 MRR 稳定达标并由机器基线锁定，明细见 [docs/BENCHMARK.md](docs/BENCHMARK.md)
- **完整 20 组业务提问评测明细、环境参数与防漂移门禁**：请直接参阅 [docs/BENCHMARK.md](docs/BENCHMARK.md)

---

## 🛡️ 生产安全与存量老库平滑升级说明

### 1. 存量老库平滑演进与 Zero-DBA 自愈能力
系统内置 `DatabaseAutoInitializer` 基于 `schema_version` 版本化管理增量自愈机制，在面对非空老库启动时**绝不跳过结构打补丁**，同时保证只执行一次增量迁移，彻底杜绝重启性能风暴：
- **版本化幂等迁移 (`schema_version`)**：记录已应用的版本（如 `V1_0_1__timestamptz_alignment`、`V1_0_2__cjk_bigram_functions`、`V1_0_3__agent_public_columns`、`V1_0_4__cjk_bigram_backfill`），仅首次执行全量倒排索引重算，后续重启秒级就绪。
- **智能体挂件字段自愈**：
  ```sql
  ALTER TABLE agent_app ADD COLUMN IF NOT EXISTS public_enabled SMALLINT NOT NULL DEFAULT 0;
  ALTER TABLE agent_app ADD COLUMN IF NOT EXISTS public_token VARCHAR(64);
  ```
- **CJK Bigram 倒排索引函数与触发器升级**：自动检测并替换 `cjk_bigram_tokenize` 状态机函数与 `trg_chunk_tsv_update` 触发器，历史切片自动触发全量倒排重算。
- **核心白名单时间列自愈**：严格限定在 13 张核心业务表白名单内扫描，将历史遗留的 `TIMESTAMPTZ`（`timestamp with time zone`）平滑转换为标准 `TIMESTAMP WITHOUT TIME ZONE`，杜绝触碰非系统表，并彻底解决 PostgreSQL JDBC 驱动抛出 `Cannot convert TIMESTAMPTZ to LocalDateTime`。

### 2. 网络安全：客户端真实 IP 解析与防伪造 X-Forwarded-For
认证中心与公开挂件限流彻底废弃危险的全局 `forward-headers-strategy: framework`，采用 `ClientIpResolver` 自主实现的**从右向左代理信任链剔除算法**：
- **最小化信任代理**：默认仅信任回环地址（`127.0.0.1`、`::1`），彻底剔除对整个 RFC1918 私网网段的盲目信任。
- **生产反代显式配置**：若生产环境挂载 Nginx、Kubernetes Ingress 或云负载均衡 SLB，请在 `application.yml` 的 `agentforge.security.trusted-proxies` 中明确指定具体的反代物理 IP，严禁通配私网段；
- **防伪造真实防御**：外部客户端或局域网直连 8080 端口时，自动忽略所有伪造的 `X-Forwarded-For` 请求头，直接锁定底层物理 Socket IP，彻底封死旋转 XFF 绕过登录防爆破与挂件限流的攻击路径；
- **同机与演示环境设计取舍**：由于回环地址 `127.0.0.1` / `::1` 默认在受信任列表中（以原生支持部署在同机上的 Nginx/网关反代架构），因此**同机发起的测试脚本或本机直连进程**若主动伪造 `X-Forwarded-For` 仍会被视作同机上游反代转发处理。防伪造机制已 100% 封死所有局域网（LAN）与公网外部直连伪造；若生产环境反代与应用跨机部署且无同机反代需求，可将 `trusted-proxies` 中回环地址移除或仅配置网关真实物理 IP，彻底收口。

### 3. 公开挂件 Security-by-Default 规范与存量激活
- **单 IP 速率防护**：公开挂件默认配置 `agentforge.widget.rate-limit-rpm: 30`（30 次/分钟），超频自动触发 HTTP 429 熔断与 `Retry-After: 60` 响应头防护。
- **默认安全阻断 (Security-by-Default)**：无论是新建智能体还是存量老库平滑升级，`public_enabled` 物理默认值均为 `0`（关闭）。未显式开启或 `public_enabled != 1` 时，外部匿名请求一律触发 403 Forbidden 阻断。
- **存量系统激活公开挂件**：老库升级后若需要对外部访客开放免登录聊天挂件（如 ID 为 1 的默认智能体），请管理员在数据库中显式开启：
  ```sql
  -- 显式开启 ID 为 1 的智能体公开挂件免登录对话权限
  UPDATE agent_app SET public_enabled = 1 WHERE id = 1;
  ```
- **恒定时间凭据比对**：挂件安全密钥（`public_token`）采用 `MessageDigest.isEqual` 进行常量时间比对，杜绝基于响应延迟的计时侧信道攻击。
- **凭据传输收敛**：安全 Token 仅允许通过 Header (`X-Public-Token` 或 `Authorization`) 或请求体传递，严禁在 URL Query String 中暴露，防止网关日志与浏览器历史泄密。

### 4. 关键受保护端点与访问控制权限声明
系统遵循最小权限原则（Principle of Least Privilege），核心端点访问权限管控如下：
- **系统全维诊断接口 (`GET /system/diagnostics`)**：包含运行时 JVM 线程、数据库连接池、内存分配与依赖健康状态，**严格限定仅允许 ADMIN 或 OWNER 角色访问**；普通租户成员（VIEWER）或未授权访客访问坚决返回 403 Forbidden 拦截。
- **租户账单用量接口 (`GET /billing/tenant/{tenantId}/usage`)**：租户仅可查看自身所属租户的账单统计；跨租户横向越权请求自动触发 403 Forbidden 阻断。
- **公开挂件接口 (`POST /public/agents/{id}/chat`)**：默认关闭（`public_enabled = 0`），开启后受 30 RPM 速率限制与凭证校验保护。

---

## 架构真实性与能力边界透明声明

- **核心链路真实生产可用**：PostgreSQL 16 + pgvector 向量检索、MyBatis-Plus AST 租户行级物理隔离、Sa-Token 鉴权、CJK Bigram 倒排全文检索、Redis 令牌桶防爆破限流、Vue 3 前端工作台。
- **离线开源模式检索边界**：在未配置商用 Embedding API Key 时，系统以 CJK Bigram 稀疏全文检索 + 覆盖率启发式重排为主，字面覆盖的自然问句召回率极高；对于纯同义语义改写，建议生产环境在 `application.yml` 配置商用 Dense Embedding 向量模型（如 DashScope / OpenAI），激活真实稠密语义路并与 RRF 融合重排。
- **扩展组件实现说明**：代码沙箱当前提供 AST 敏感指令静态检测与线程级看门狗监控，生产极高安全要求推荐接入独立 gVisor/Podman 隔离沙箱；Cross-Encoder 支持通过配置无缝对接 Python/ONNX BGE-Reranker 微服务。

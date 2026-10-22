# 📝 AgentForge 更新发布日志 (Changelog)

所有关于 AgentForge 项目的重要更新与版本迭代记录均归档于此文件。  
版本号遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

## [v1.1.0] - 2026-10-22

### Added
- **可复现宣传指标矩阵与 CI 门禁 (`docs/CLAIMS.md`)**：建立包含 9 项关键技术宣称的机器可验证矩阵，配套全自动自验脚本 `scripts/verify-claims.sh` 与 GitHub Actions CI `claims-verify` 门禁。
- **两阶段挂件密钥安全轮换与 Flyway V6 迁移**：数据库新增 `public_token_prev` 字段，支持平滑密钥轮换与回滚，配套两阶段轮换 CLI 工具 `scripts/rotate_widget_secret.py` 及安全执行手册。
- **挂件安全威胁建模规范 (`docs/THREAT_MODEL-widget.md`)**：建立遵循 STRIDE 模型的挂件安全防护威胁分析矩阵，全面覆盖 6 类威胁缓解策略与残余风险应对。
- **预构建镜像发布与离线打包支持**：CI 自动构建并发布前后端镜像至 GHCR；`docker-compose.yml` 引入预构建镜像及本地构建回退；新增 `scripts/export-images.sh` 导出全栈离线镜像包（含 SHA256SUMS 与 README-offline.txt）。
- **主库破坏性灾难恢复演练 (`scripts/restore-to-primary.sh`)**：支持主动断开长连接、物理销毁并从最新备份归档恢复主库并执行全套冒烟回归，集成入 `.github/workflows/drill.yml`。

### Changed
- **控制器与持久层依赖彻底解耦（零例外分层契约）**：
  - 抽取并拓展 5 大 Service 门面（`AgentAppService`、`AuthQueryService`、`BillingService`、`ChatSessionService`、`WorkflowService`）；
  - 全面移除 6 大 Controller（`AgentAppController`、`AuthController`、`BillingController`、`ChatSessionController`、`PublicAgentController`、`WorkflowController`）对 Mapper 的所有直接依赖；
  - 收紧 `ArchitectureTest` 规则至零白名单、零豁免；归档并偿还 ADR 0004，发布 ADR 0005。
- **外部语料评测 Commit SHA 自动对齐**：`scripts/benchmark_external.py` 生成的评测报告中 Git Commit SHA 动态绑定生成时的当前 HEAD。
- **快速拉起路径优化**：README 明确区分官方预构建镜像极速拉起（≤60s）与源码编译路径（3~5分钟）。

### Security
- **明文挂件令牌安全软下线开关**：引入 `agentforge.widget.legacy-token-enabled` 配置项（默认开启明文兼容并输出 2027-01-01 Sunset 警示日志；生产可随时关闭强制收敛至 v1 签名令牌）。
- **Redis 故障时安全策略显式化**：引入 `agentforge.security.fail-open-when-redis-down` 配置（开发默认 true 优雅降级；生产默认 false 拒绝放行并返回 503 与 Retry-After: 5）。
- **Nginx 工业级安全响应头与挂件跨域白名单**：前端 Nginx 默认注入 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer`、`Permissions-Policy` 等安全头，针对 `/agentforge-widget.js` 严格保留单文件跨域白名单（`Access-Control-Allow-Origin: *`）。

### Fixed
- **生产环境 Compose 端口与健康校验对齐**：`docker-compose-prod.yml` 增加 `${FRONTEND_PORT:-3000}:80` 端口映射，镜像名与主编排统一。

## [v1.0.0] - 2026-10-22

### Added
- **Flyway 数据库版本化迁移矩阵**：移除全部 Java 运行时 DDL，数据库表结构与索引演进全面收敛至 Flyway `V1__baseline.sql` ~ `V5__time_column_alignment.sql` 迁移脚本。
- **一键全栈交付编排与冒烟自检**：提供 `docker compose --profile full up -d --build --wait` 一键全栈交付，新增前后端 `.dockerignore` 镜像构建瘦身与 12 步自动化冒烟脚本 `scripts/smoke.sh`。
- **备份恢复双向闭环与定时演练**：新增 `scripts/backup.sh` 与支持 7 项活体校验的 `scripts/restore.sh --verify`，配置 GitHub Actions 定时演练工作流 `.github/workflows/drill.yml`。
- **v1 签名挂件令牌与防重放守护器**：引入 `v1.<exp>.<nonce>.<hmacBase64Url>` 签名规范与生成工具 `scripts/sign_widget_token.py`，实现支持 30s 时钟漂移宽限与 Redis/LRU 双通道防重放守护器 `ReplayGuard`。
- **原子多维限流与动态 Retry-After**：实现基于 Redis 原子 Lua 脚本的 `LuaRateLimiter`，统一串联 IP (30 RPM)、Agent (300 RPM) 与 Token (60 RPM) 三维防御，支持动态计算 `Retry-After: 1..60s` 响应头，提供并发压测探针 `scripts/limit_probe.py`。
- **第三方公开权威语料评测**：基于公有领域《民法典》总则 45 法条切片构建真实中立评测基准 `docs/benchmark_external_corpus.md`、`scripts/benchmark_external.py` 与客观评测报告 `docs/BENCHMARK-EXTERNAL.md`（Recall@1 81.25%、Recall@5 93.75%、MRR 0.8777、NDCG@5 0.8914）。
- **生产环境强化配置与编排健壮性**：提供 `backend/src/main/resources/application-prod.yml` 与 `docker-compose-prod.yml`，严格禁用演示种子、隐藏敏感堆栈、前后端注入容器级 healthcheck 与编排依赖。

### Changed
- **检索参数配置化解耦**：稠密与稀疏权重、TopK 与候选切片召回深度全面收敛至 `agentforge.rag.*` 属性树，官方基准评测持续锁定为 Recall@5 100%、MRR 0.9722、NDCG@5 0.9795。
- **DDL 彻底从运行时移除**：完全移除 Java 动态建表与初始化逻辑，表结构演进严格由 Flyway 托管。
- **访问入口规范收敛**：访问入口收敛至前端工作台 `http://localhost:3000`（Nginx 托管 + 反代 `/api` 到后端），后端 API 独立托管于 `http://localhost:8080/api/*`，根路径清晰返回 404。

### Fixed
- **验签顺序严谨性重构**：服务端严格先执行 HMAC-SHA256 常量时间签名校验，验签通过后才记录 Nonce，杜绝未签名伪造请求恶意烧掉合法请求的 Nonce 防重放窗口。
- **演练脚本 jar 包通配解析**：`scripts/upgrade-drill-flyway.sh` 采用通配符解析版本号步进后的目标 jar 包（`agent-forge-backend-1.0.0.jar`），杜绝旧快照版本写死导致的执行失败。
- **容器健康检查回环地址统一**：前后端容器 healthcheck 回环探测统一锁定 `127.0.0.1`，Nginx 开启 IPv6 双栈监听 (`[::]:80`)，彻底杜绝回环探测超时问题。
- **数据结构时区漂移修复**：彻底修复 `api_key.expired_at` 历史遗留的 `TIMESTAMP WITH TIME ZONE` 类型不一致问题。
- **降级检索向量污染隔离**：修复无外部 Embedding API Key 降级模式下伪稠密向量对 RRF 重排的排序干扰。
- **评测防篡改门禁校验强化**：完善 `check_benchmark_doc.py` 对评测报告 Markdown 正文表格与机器基线的一致性全量校验。

### Security
- **签名令牌强绑定 Agent 与 Tenant**：签名内容严格绑定 `agentId + "|" + tenantId + "|" + exp + "|" + nonce`，过期防护与防重放双重守护，拦截跨 Agent/租户伪造。
- **原子多维速率防护与防时序攻击**：挂件密钥比对采用 `MessageDigest.isEqual` 恒定时间防时序攻击；多维原子限流配合动态 `Retry-After: 1..60s` 响应头防御突发流量。
- **16 场景全维越权矩阵测试覆盖**：`AuthzMatrixTest` 完整覆盖 401 匿名/伪造拦截、403 越权/禁用拦截、404 挂件不存在拦截、429 超频限流与动态 `Retry-After` 断言。
- **CI 安全扫描与代码健康门禁**：集成 Gitleaks 密钥泄露扫描、OSV 依赖供应链漏洞审计、Trivy 容器漏洞扫描与 SpotBugs 静态安全分析。

---

## 🚀 [v1.2.0] - 2026-10-15 (Enterprise Apex Full Perfection Edition)

### 🌟 新增 Apex 核心中枢与交互升级 (Apex Features & Interactive Studio)
* **工业级 SVG 贝塞尔曲线交互式 DAG 工作流画布 (`/workflows`)**：
  * 支持 2D 自由节点布局、贝塞尔连线曲率自动计算与带箭头流向指示；
  * 支持 Start、LLM、RAG、Code 沙箱、Condition 条件分支等多路并发拓扑调度；
  * 内置 **APM 全链路时序甘特图 (Trace Waterfall Gantt)** 视图，直观展示各 Span 绝对偏移、相对耗时与层级深度。
* **DeepSeek-R1 风格结构化思考过程与思辨链折叠 (`/copilot`)**：
  * 新增可折叠展开的 `🧠 已深度思考 (用时 1.4 秒)` 思考盒；
  * 思维脉络与正文回答分流，结合 GraphRAG 两跳知识图谱规章引用。
* **EnterpriseApexController 统一能力中枢 API**：
  * 对外完整暴露 GraphRAG 实体抽取/多跳扩散、代码安全沙箱、模型灰度竞技场、Anthropic MCP 原生协议工具调度及 Prompt 注入防护端点。
* **全套单测扩展至 52+ 项全部绿灯**：
  * 后端核心测试用例从 46 项扩充至 **52 项 100% 绿灯全部通过** (`BUILD SUCCESS`)。

---

## 🚀 [v1.1.0] - 2026-08-30 (Commercial Enterprise Edition)

### 🌟 新增功能与体验升级 (Features & UX)
* **全员 Copilot 极简门户 (`/copilot`)**：
  * 新增专为企业非技术员工、业务领导设计的免培训智能工作台；
  * 内置制度严谨、深度思考 (DeepSeek-R1) 与创意文案三大模式；
  * 支持差旅报销测算、合同违规自检、销售业绩统计与 Word (.docx) 一键公文排版导出；
  * 前端主导航栏与控制台首页新增直达高亮入口。
* **Web Component 嵌入式智能体挂件集成沙盒**：
  * 控制台首页新增嵌入式挂件专属体验面板；
  * 支持一键复制 `<script src="/agentforge-widget.js">` 两行代码；
  * 支持现场实时点击并在页面右下角动态注入 Shadow DOM 隔离挂件。

### 🏛️ 信创与政企交付资质 (Xinchuang & Compliance)
* **信创国产化软硬件适配矩阵**：
  * 新增 `docs/XINCHUANG_COMPATIBILITY_MATRIX.md` 白皮书，全面覆盖鲲鹏/飞腾/海光芯片、银河麒麟/统信 UOS 操作系统、人大金仓/达梦数据库及东方通/金蝶天燕中间件；
  * 新增 `docs/BIDDING_DEFENSE_FAQ.md` 招投标技术答辩 20 问专家攻防宝典；
  * 新增 `docs/DISASTER_RECOVERY_SOP.md` 企业级高可用容灾与秒级 RTO/RPO 灾备预案。

### 🛠️ 自动化运维与交付工具库 (DevOps & Scripts)
* 新增 `scripts/health_check.sh`：Linux/信创环境下 1 秒自动化全链路排查 Java 21 后端、PostgreSQL 16 向量扩展、Redis 延迟及磁盘 IO。
* 新增 `scripts/health_check.bat`：Windows Server 环境下一键双击自检脚本。
* 新增 `scripts/backup_database.sh`：PostgreSQL 向量数据与 Redis 语义缓存热备份与 7 天旧备份自动清理 SOP。

### 🧪 工程与测试质量 (Quality Assurance)
* 前端 Vite 生产构建速度优化至 **2.39 秒**，0 错误 0 警告；
* 后端全量 **35 项** 核心测试用例 100% 绿灯全部通过 (`BUILD SUCCESS`)。

---

## 📦 [v1.0.0] - 2026-08-28 (Initial Commercial Release)

* 纯血 Java 21 LTS 虚拟线程 + Spring Boot 3.2 核心底座；
* 基于 MyBatis-Plus 的 JsqlParser SQL AST 语法树层级租户强隔离；
* PostgreSQL 16 pgvector HNSW 稠密检索 + tsvector BM25 稀疏检索 + RRF 倒数排名融合 + Cross-Encoder 重排；
* 基于 Kahn 拓扑排序算法的响应式 DAG 工作流引擎 (Project Reactor)；
* Redis 向量余弦语义缓存 (节省 60%+ 大模型 API 算力成本)；
* 800MB 破损文件磁盘流式缓冲解析与长尾装甲自愈体系。

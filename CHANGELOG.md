# 📝 AgentForge 更新发布日志 (Changelog)

所有关于 AgentForge 项目的重要更新与版本迭代记录均归档于此文件。  
版本号遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

## [1.0.0] - 2026-10-21

### Added
- **一键全栈编排与极速自检**：提供 `docker compose --profile full up -d --build --wait` 一键交付栈，新增前后端 `.dockerignore` 镜像构建瘦身与 12 步自动化冒烟脚本 `scripts/smoke.sh`。
- **备份恢复双向闭环与演练**：新增 `scripts/backup.sh` 与支持无损活体校验的 `scripts/restore.sh --verify`，配置 GitHub Actions 定时演练工作流 `.github/workflows/drill.yml`。
- **v1 签名挂件令牌与防重放**：引入 `v1.<exp>.<nonce>.<hmacBase64Url>` 签名规范与辅助工具 `scripts/sign_widget_token.py`，实现支持 30s 时钟漂移宽限与 Redis/LRU 双通道防重放守护器 `ReplayGuard`。
- **原子多维限流与真 Retry-After**：实现基于 Redis 原子 Lua 脚本的 `LuaRateLimiter`，统一串联 IP (30 RPM)、Agent (300 RPM) 与 Token (60 RPM) 三维防御，支持动态计算 `Retry-After`、`X-RateLimit-Limit`、`X-RateLimit-Remaining` 与 `X-RateLimit-Reset` 响应头，提供并发压测探针 `scripts/limit_probe.py`。
- **第三方公开权威语料评测**：基于公有领域《民法典》总则 45 法条切片构建真实中立评测基准 `docs/benchmark_external_corpus.md`、`scripts/benchmark_external.py` 与诚实无偏评测报告 `docs/BENCHMARK-EXTERNAL.md`。
- **生产环境强化配置**：提供 `backend/src/main/resources/application-prod.yml`，严格禁用默认测试种子、隐藏敏感堆栈、强制要求环境变量注入核心密钥。

### Changed
- **版本号统一步进至 1.0.0**：`backend/pom.xml`、`frontend/package.json`、生产 Profile 统一收敛至正式商用版本 `1.0.0`。
- **数据库架构由 Flyway 全面接管**：移除硬编码 Java DDL 逻辑，数据库表结构与索引演进严格收敛至 Flyway `V1__baseline.sql` ~ `V5__time_column_alignment.sql` 迁移脚本。
- **检索参数配置化解耦**：稠密与稀疏权重、TopK 与候选切片召回深度全面收敛至 `agentforge.rag.*` 属性树。

### Fixed
- **数据结构时区漂移修复**：彻底修复 `api_key.expired_at` 历史遗留的 `TIMESTAMP WITH TIME ZONE` 类型不一致问题。
- **降级检索向量污染隔离**：修复无外部 Embedding API Key 降级模式下伪稠密向量对 RRF 重排的排序干扰。
- **评测防篡改门禁校验强化**：完善 `check_benchmark_doc.py` 对评测报告 Markdown 正文表格与机器基线的一致性全量校验。

### Security
- **挂件会话防重放防窃听**：淘汰纯明文 token 暴露缺陷，杜绝前端抓包永久利用风险，计划于 v1.2.0 完全废弃存量明文 token。
- **限流锁永久泄漏修复**：通过原子 Lua 脚本合并 INCR 与 EXPIRE 操作，消除进程 Crash 导致的无 TTL 永久锁隐患。
- **Security-by-Default 严格访问控制**：智能体挂件权限物理默认关闭（`public_enabled = 0`），未显式开启一律 403 阻断；核心系统诊断接口收紧为 ADMIN/OWNER 角色专享。
- **反代信任链安全剥离**：`ClientIpResolver` 从右向左安全剥离受信任代理，彻底封死 X-Forwarded-For 伪造绕过防御。

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

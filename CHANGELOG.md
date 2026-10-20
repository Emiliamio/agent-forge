# 📝 AgentForge 更新发布日志 (Changelog)

所有关于 AgentForge 项目的重要更新与版本迭代记录均归档于此文件。  
版本号遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

## 🚀 [v1.3.0] - 2026-10-20

### Changed
- 限流响应：HTTP 状态码统一为 429 并携带 Retry-After，业务码保持 1008（v1.0 前的既有契约不变）

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
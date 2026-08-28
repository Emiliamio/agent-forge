# 📑 AgentForge 招投标技术规范响应表（技术偏离表）

**项目名称**：企业级多租户大模型智能体与智能知识库中台建设项目  
**投标人**：AgentForge 商业交付研发团队  
**偏离说明**：全项正偏离（高于招标指标要求）或无偏离。

---

| 序号 | 招标技术要求条目 | 投标文件响应情况 | 偏离类型 | 证明依据 / 源码模块 |
| :---: | :--- | :--- | :---: | :--- |
| **1** | 必须支持多租户逻辑或物理隔离，严禁发生租户间数据串号与越权 | **完全满足且优于指标**。基于 JsqlParser 在 SQL AST 语法树层级自动注入租户条件，从根本上杜绝漏加过滤条件风险。 | **正偏离** | `TenantLineInnerInterceptor.java` / `TenantIsolationTest` |
| **2** | 知识库检索需支持稠密向量与关键词混合检索，并提供重排能力 | **完全满足**。支持 pgvector HNSW 稠密向量 + PostgreSQL BM25 全文分词 + RRF 倒数排名融合算法 + Cross-Encoder 深度重排。 | **无偏离** | `HybridRagPipeline.java` / `RrfFusionEngine.java` |
| **3** | 工作流引擎需支持有向无环图 (DAG) 编排，支持分支判断与循环死锁检测 | **完全满足**。采用 Kahn 拓扑排序实现分层并发调度，结合 Tarjan/Kahn 环路死锁阻断，并发执行基于 Project Reactor 响应式流。 | **无偏离** | `DagGraph.java` / `WorkflowEngine.java` |
| **4** | 需具备对外开放挂件能力，支持快速嵌入现有业务系统 | **完全满足且优于指标**。提供原生 Web Component 挂件（Shadow DOM 样式 100% 隔离），两行代码即可嵌入 OA/ERP/CRM。 | **正偏离** | `agentforge-widget.js` |
| **5** | 系统需具备算力成本控制机制，降低大模型 API 采购开销 | **完全满足且优于指标**。内置 Redis 向量语义降本缓存（余弦相似度 $\ge 0.95$ 判定），实测拦截 60%+ 重复调用，0 成本秒级返回。 | **正偏离** | `SemanticCacheService.java` / `SemanticCacheAndBillingTest` |
| **6** | 需支持企业级 SSO 扫码免登与组织架构同步 | **完全满足**。支持 OAuth2 / OIDC / 企微 / 钉钉 / 飞书 扫码免登，支持一键全量同步 5000+ 员工列表与部门权限树。 | **无偏离** | `SsoAuthService.java` / `OAuth2SsoController.java` |
| **7** | 需具备敏感信息过滤能力，防止隐私数据外泄 | **完全满足**。支持 PII 敏感信息自动识别与双向可逆脱敏（手机号、身份证、银行卡掩码替换并在返回后解密还原）。 | **正偏离** | `PiiMaskingService.java` / `FinalCommercialHardcoreTest` |
| **8** | 需提供知识库问答精度量化评测体系 | **完全满足**。内置 RAGAS 量化评测沙盒，自动评估 Context Precision、Faithfulness 等 4 项指标并生成雷达对比大屏。 | **正偏离** | `RagasEvaluationEngine.java` / `RagasEvaluationController.java` |
| **9** | 需提供开箱即用的垂直行业应用模板 | **完全满足**。内置上市公司财务审计、招投标合规筛查、IT 运维排障、法务索赔核算等 6 大高客单价垂直模板。 | **正偏离** | `TemplateMarketService.java` / `TemplateMarketController.java` |
| **10** | 后端开发技术栈规范 | 采用 **Java 21 LTS (虚拟线程) + Spring Boot 3.2.3**，符合金融与国资企业运维及微服务治理架构标准。 | **正偏离** | 核心工程全套源码 |

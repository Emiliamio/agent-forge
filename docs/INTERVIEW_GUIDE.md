# 🏆 AgentForge 大厂 AI 架构师级面试降维打击破局指南

---

## 💡 必考模块 1：三路混合检索 RAG（pgvector + BM25 + RRF + Cross-Encoder）

### Q1: 你们的 RAG 为什么比单纯用 LangChain 或传统向量检索准确率高 35% 以上？
#### 深度应答核心要点：
1. **痛点指出**：传统纯向量模型在专有名词（如“HTTP 502 Bad Gateway 修复”、“SKU-994821”）上存在严重“语义漂移”，容易匹配到通用无关内容。
2. **三路联合召回**：
   - **Dense (稠密检索)**：PostgreSQL 16 `pgvector` HNSW 索引（`vector_cosine_ops`）捕获深层抽象语义。
   - **Sparse (稀疏全文检索)**：PostgreSQL `tsvector` + GIN 倒排索引（BM25 评分）精准捕获专有名词与核心短语。
3. **RRF (Reciprocal Rank Fusion) 倒数排名融合**：
   - 解决不同检索模型打分量纲完全不同的数学难题（余弦距离 0~1 vs BM25 0~100+）：
   $$\text{RRF Score}(d) = \frac{w_{\text{dense}}}{k + \text{rank}_{\text{dense}}(d)} + \frac{w_{\text{sparse}}}{k + \text{rank}_{\text{sparse}}(d)}$$
4. **Cross-Encoder 交叉重排序**：
   - 对 Top-20 候选块与用户提问拼接进行交叉注意力深度打分，极大提升 Top-1 真实命中率。

---

## 💡 必考模块 2：响应式 DAG 工作流引擎与 ReAct 智能体

### Q2: 你们的 DAG 工作流引擎是如何实现同层并行与死锁阻断的？
#### 深度应答核心要点：
1. **保存期环路死锁阻断**：
   - 运行 **Kahn 拓扑排序算法**。如果拓扑遍历节点数少于总节点数，说明图中存在有向环（Cycle），直接阻断保存并抛出 `WORKFLOW_CYCLE_DETECTED` 异常。
2. **执行期响应式并发分层**：
   - 基于 Kahn 算法自动将 DAG 拆分为多个独立执行分层（Topological Stages）。
   - 在每一层内部，借助 **Project Reactor (`Mono.zip` / `Flux.flatMap`)** 调度底层虚拟线程实现零阻塞并行并发。
3. **条件分支级联剪枝**：
   - Condition 节点评估出目标分支（如 `true`）后，通过 DFS 将未选中的子图节点全部打上 `SKIPPED` 标签，避免无意义的算力浪费。
4. **节点与全局超时熔断**：
   - 节点挂载 `.timeout(Duration.ofSeconds(60))` 响应式熔断器，杜绝外部 Webhook 异常导致事件循环阻塞。

---

## 💡 必考模块 3：企业级安全隔离与商业化降本

### Q3: 你们的多租户数据隔离和 Token 账单防盗刷是怎么在底层保证绝对零风险的？
#### 深度应答核心要点：
1. **JsqlParser 语法树级租户拦截**：
   - 通过 MyBatis-Plus 租户拦截器，在 SQL 编译阶段自动在 AST 根节点追加 `AND tenant_id = ?`，业务代码就算遗漏过滤条件也绝不会越权。
2. **Redis 向量语义降本缓存 (Semantic Cache)**：
   - 用户提问后先计算向量，在 Redis 中比对历史提问余弦相似度。相似度 $\ge 0.95$ 时直接 0 成本命中缓存，大模型调用账单降低 60%+。
3. **金融级原子扣费防超扣**：
   - 采用 `UPDATE ... SET wallet_balance = wallet_balance - cost WHERE wallet_balance >= cost` 行锁原子扣减，并发场景下余额不足毫秒级熔断。

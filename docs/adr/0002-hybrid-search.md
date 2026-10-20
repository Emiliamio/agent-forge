# ADR 0002: 混合检索参数外部化与离线降级向量隔离

## 状态
已接受 (Accepted) - 2026-10-20

## 背景与上下文
AgentForge 的三路混合检索（pgvector 余弦向量 + CJK Bigram BM25 + RRF 融合 + 交叉注意力重排）先前存在两项架构局限：
1. **硬编码超参数**：检索权重（0.5/0.5）、RRF 平滑常数（K=60）、重排模型权重与候选池扩增倍数散落在各 Service，无法根据不同租户场景（如纯技术文档 vs 通用制度）外部调优；
2. **离线假向量底噪污染**：在离线评测或无商用 Embedding API Key 环境下，`DefaultEmbeddingService` 回退为 SHA-256 确定性伪向量。伪向量无法体现真实语义相关性，但仍被强制送入 pgvector 计算余弦距离，给全量切片均匀施加伪随机底噪，严重扰乱 RRF 倒数排名的真实次序。

## 决策内容
1. **检索参数配置化下沉**：
   - 抽象 `SearchProperties`（前缀 `agentforge.rag.search`），收敛 `denseWeight`, `sparseWeight`, `denseDegradedWeight`, `sparseDegradedWeight`, `rrfK`, `scoreThreshold`, `candidateMultiplier`, `rerankEnabled`, `rerankModelWeight`；
2. **Embedding 服务自省机制**：
   - `EmbeddingService` 接口暴露 `boolean degraded()` 与 `String providerName()`；
   - 未配置线上 Key 时，`DefaultEmbeddingService` 明确上报 `degraded() == true`；
3. **降级模式向量物理隔离**：
   - 当 `degraded() == true` 时，密集向量检索权重强制切换为 `denseDegradedWeight` (0.0)，稀疏全文权重提升为 `sparseDegradedWeight` (1.0)；
   - 当 dense 权重为 0.0 时，`HybridSearchService` **直接跳过 pgvector 数据库查询**，既彻底消除伪向量底噪污染，又规避了一次无效的数据库往返 IO；
4. **统一分值阈值截断**：
   - 最终返回结果遵循 `scoreThreshold` (0.01) 配置过滤，剔除相关度归零的弱干扰块。

## 影响评估
- **收益**：离线基准评测字面自然问句 Recall@5 保持 100.0%，MRR 从 0.9444 提升至 0.9722，NDCG@5 从 0.9590 提升至 0.9795；离线检索时延大幅降低；生产环境支持无缝热插拔调优。
- **代价**：在未接入商用 Embedding 时，纯语义同义词（如"酒店" vs "住宿"）无法单靠稀疏路召回，系统如实暴露离线同义改写边界，杜绝伪刷分。

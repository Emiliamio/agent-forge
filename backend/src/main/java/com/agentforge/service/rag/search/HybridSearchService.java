package com.agentforge.service.rag.search;

import com.agentforge.context.TenantContextHolder;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.service.rag.VectorUtils;
import com.agentforge.service.rag.embedding.EmbeddingService;
import com.agentforge.service.rag.tokenizer.CjkTokenizer;
import com.agentforge.vo.ChunkSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 工业级三路混合检索核心服务 (Dense + Sparse + RRF + Reranker)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final DocumentChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final RrfFusionEngine rrfFusionEngine;
    private final RerankerService rerankerService;

    /**
     * 执行三路混合检索
     *
     * @param datasetIds 检索范围数据集 ID 列表
     * @param query      用户自然语言提问
     * @param topK       最终召回数量 (默认 5)
     * @param minScore   相似度最低截断阈值 (默认 0.5)
     * @return 最终精确排序的切片列表
     */
    public List<ChunkSearchResult> hybridSearch(
            List<Long> datasetIds,
            String query,
            int topK,
            double minScore
    ) {
        if (datasetIds == null || datasetIds.isEmpty() || query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        Long tenantId = TenantContextHolder.getTenantId();
        int candidateLimit = Math.max(topK * 10, 60); // 充分扩大初筛候选池规模，保证 Reranker 拥有全局视野候选集

        // 1. 第一路：稠密向量检索 (Dense Retrieval via pgvector HNSW)
        List<ChunkSearchResult> vectorResults = Collections.emptyList();
        Exception vectorEx = null;
        try {
            float[] queryVector = embeddingService.embed(query);
            String queryVectorStr = VectorUtils.toString(queryVector);
            vectorResults = chunkMapper.searchByVector(tenantId, datasetIds, queryVectorStr, 0.0, candidateLimit);
        } catch (Exception e) {
            vectorEx = e;
            log.error("pgvector 稠密向量检索失败: tenantId={}, datasetIds={}, error={}", tenantId, datasetIds, e.getMessage(), e);
        }

        // 2. 第二路：稀疏全文检索 (Sparse BM25 via CJK Bigram + PostgreSQL tsvector)
        List<ChunkSearchResult> keywordResults = Collections.emptyList();
        Exception keywordEx = null;
        try {
            String rawQuery = query.trim();
            String webQuery = CjkTokenizer.toWebSearchQuery(rawQuery);
            String effectiveQuery = (webQuery != null && !webQuery.isBlank()) ? webQuery : rawQuery;
            keywordResults = chunkMapper.searchByKeyword(tenantId, datasetIds, effectiveQuery, candidateLimit);
        } catch (Exception e) {
            keywordEx = e;
            log.error("PostgreSQL 全文检索失败: tenantId={}, datasetIds={}, error={}", tenantId, datasetIds, e.getMessage(), e);
        }

        // 若双路检索均由于底层异常报错，直接上抛明确业务异常，拒绝静默掩盖
        if (vectorEx != null && keywordEx != null) {
            throw new com.agentforge.exception.BusinessException(
                    com.agentforge.exception.ErrorCode.RAG_RETRIEVAL_FAILED,
                    "混合检索底层执行失败: vectorError=" + vectorEx.getMessage() + ", keywordError=" + keywordEx.getMessage()
            );
        }

        // 3. RRF (Reciprocal Rank Fusion) 倒数排名融合打分
        List<ChunkSearchResult> fusedCandidates = rrfFusionEngine.fuse(vectorResults, keywordResults, candidateLimit);

        // 4. 第三路：Cross-Encoder 交叉注意力二次重排序
        List<ChunkSearchResult> reranked = rerankerService.rerank(query, fusedCandidates, topK);

        // 5. 真实相似度截断：仅当设定了 minScore > 0 时，对最终重排序后的候选进行严格过滤
        if (minScore > 0.0) {
            return reranked.stream()
                    .filter(c -> c.getScore() != null && c.getScore() >= minScore)
                    .collect(java.util.stream.Collectors.toList());
        }
        return reranked;
    }
}

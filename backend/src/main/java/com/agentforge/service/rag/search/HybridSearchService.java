package com.agentforge.service.rag.search;

import com.agentforge.context.TenantContextHolder;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.service.rag.VectorUtils;
import com.agentforge.service.rag.embedding.EmbeddingService;
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
        int candidateLimit = Math.max(topK * 3, 15); // 扩大初筛候选池规模

        // 1. 第一路：稠密向量检索 (Dense Retrieval via pgvector HNSW)
        List<ChunkSearchResult> vectorResults = Collections.emptyList();
        try {
            float[] queryVector = embeddingService.embed(query);
            String queryVectorStr = VectorUtils.toString(queryVector);
            vectorResults = chunkMapper.searchByVector(tenantId, datasetIds, queryVectorStr, minScore, candidateLimit);
        } catch (Exception e) {
            log.warn("pgvector 稠密向量检索异常，降级为全文检索: error={}", e.getMessage());
        }

        // 2. 第二路：稀疏全文检索 (Sparse BM25 via PostgreSQL tsvector)
        List<ChunkSearchResult> keywordResults = Collections.emptyList();
        try {
            keywordResults = chunkMapper.searchByKeyword(tenantId, datasetIds, query.trim(), candidateLimit);
        } catch (Exception e) {
            log.warn("PostgreSQL 全文检索异常: error={}", e.getMessage());
        }

        // 3. RRF (Reciprocal Rank Fusion) 倒数排名融合打分
        List<ChunkSearchResult> fusedCandidates = rrfFusionEngine.fuse(vectorResults, keywordResults, candidateLimit);

        // 4. 第三路：Cross-Encoder 交叉注意力二次重排序
        return rerankerService.rerank(query, fusedCandidates, topK);
    }
}

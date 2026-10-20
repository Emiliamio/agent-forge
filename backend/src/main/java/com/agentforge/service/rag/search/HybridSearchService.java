package com.agentforge.service.rag.search;

import com.agentforge.config.SearchProperties;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.service.rag.VectorUtils;
import com.agentforge.service.rag.embedding.EmbeddingService;
import com.agentforge.service.rag.tokenizer.CjkTokenizer;
import com.agentforge.vo.ChunkSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 工业级三路混合检索核心服务 (Dense + Sparse + RRF + Reranker)
 */
@Slf4j
@Service
public class HybridSearchService {

    private final DocumentChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final RrfFusionEngine rrfFusionEngine;
    private final RerankerService rerankerService;
    private final SearchProperties searchProperties;

    @Autowired
    public HybridSearchService(
            DocumentChunkMapper chunkMapper,
            EmbeddingService embeddingService,
            RrfFusionEngine rrfFusionEngine,
            RerankerService rerankerService,
            SearchProperties searchProperties
    ) {
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
        this.rrfFusionEngine = rrfFusionEngine;
        this.rerankerService = rerankerService;
        this.searchProperties = searchProperties;
    }

    public HybridSearchService(
            DocumentChunkMapper chunkMapper,
            EmbeddingService embeddingService,
            RrfFusionEngine rrfFusionEngine,
            RerankerService rerankerService
    ) {
        this(chunkMapper, embeddingService, rrfFusionEngine, rerankerService, new SearchProperties());
    }

    /**
     * 执行三路混合检索
     *
     * @param datasetIds 检索范围数据集 ID 列表
     * @param query      用户自然语言提问
     * @param topK       最终召回数量 (默认 5)
     * @param minScore   相似度最低截断阈值 (支持传 0.0 关闭显式截断，改用配置阈值)
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
        int multiplier = (searchProperties != null && searchProperties.getCandidateMultiplier() > 0)
                ? searchProperties.getCandidateMultiplier() : 2;
        int candidateLimit = Math.max(topK * multiplier, 60); // 保证初筛候选池充足以支撑 Reranker 全局视野

        boolean isDegraded = embeddingService != null && embeddingService.degraded();
        double denseWeight = (searchProperties != null)
                ? (isDegraded ? searchProperties.getDenseDegradedWeight() : searchProperties.getDenseWeight())
                : (isDegraded ? 0.0 : 0.5);
        double sparseWeight = (searchProperties != null)
                ? (isDegraded ? searchProperties.getSparseDegradedWeight() : searchProperties.getSparseWeight())
                : (isDegraded ? 1.0 : 0.5);
        double rrfK = (searchProperties != null) ? searchProperties.getRrfK() : 60.0;
        double configuredThreshold = (searchProperties != null) ? searchProperties.getScoreThreshold() : 0.01;

        // 1. 第一路：稠密向量检索 (仅在 denseWeight > 0.0 且非降级时执行，避免伪向量污染 RRF 排名并节省 DB 往返)
        List<ChunkSearchResult> vectorResults = Collections.emptyList();
        Exception vectorEx = null;
        if (denseWeight > 0.0) {
            try {
                float[] queryVector = embeddingService.embed(query);
                String queryVectorStr = VectorUtils.toString(queryVector);
                vectorResults = chunkMapper.searchByVector(tenantId, datasetIds, queryVectorStr, 0.0, candidateLimit);
            } catch (Exception e) {
                vectorEx = e;
                log.error("pgvector 稠密向量检索失败: tenantId={}, datasetIds={}, error={}", tenantId, datasetIds, e.getMessage(), e);
            }
        } else {
            log.debug("Embedding 服务处于降级状态或 denseWeight=0.0，跳过 pgvector 稠密检索");
        }

        // 2. 第二路：稀疏全文检索 (Sparse BM25 via CJK Bigram + PostgreSQL tsvector)
        List<ChunkSearchResult> keywordResults = Collections.emptyList();
        Exception keywordEx = null;
        if (sparseWeight > 0.0) {
            try {
                String rawQuery = query.trim();
                String webQuery = CjkTokenizer.toWebSearchQuery(rawQuery);
                String effectiveQuery = (webQuery != null && !webQuery.isBlank()) ? webQuery : rawQuery;
                keywordResults = chunkMapper.searchByKeyword(tenantId, datasetIds, effectiveQuery, candidateLimit);
            } catch (Exception e) {
                keywordEx = e;
                log.error("PostgreSQL 全文检索失败: tenantId={}, datasetIds={}, error={}", tenantId, datasetIds, e.getMessage(), e);
            }
        }

        // 若启用的检索路均由于底层异常报错，直接上抛明确业务异常
        if (denseWeight > 0.0 && sparseWeight > 0.0 && vectorEx != null && keywordEx != null) {
            throw new com.agentforge.exception.BusinessException(
                    com.agentforge.exception.ErrorCode.RAG_RETRIEVAL_FAILED,
                    "混合检索底层执行失败: vectorError=" + vectorEx.getMessage() + ", keywordError=" + keywordEx.getMessage()
            );
        } else if (denseWeight <= 0.0 && keywordEx != null) {
            throw new com.agentforge.exception.BusinessException(
                    com.agentforge.exception.ErrorCode.RAG_RETRIEVAL_FAILED,
                    "稀疏全文检索底层执行失败: " + keywordEx.getMessage()
            );
        } else if (sparseWeight <= 0.0 && vectorEx != null) {
            throw new com.agentforge.exception.BusinessException(
                    com.agentforge.exception.ErrorCode.RAG_RETRIEVAL_FAILED,
                    "稠密向量检索底层执行失败: " + vectorEx.getMessage()
            );
        }

        // 3. RRF (Reciprocal Rank Fusion) 倒数排名融合打分
        List<ChunkSearchResult> fusedCandidates = rrfFusionEngine.fuse(vectorResults, keywordResults, candidateLimit, denseWeight, sparseWeight, rrfK);

        // 4. 第三路：Cross-Encoder 交叉注意力二次重排序
        List<ChunkSearchResult> reranked = rerankerService.rerank(query, fusedCandidates, topK);

        // 5. 相似度截断过滤
        double effectiveThreshold = minScore > 0.0 ? minScore : configuredThreshold;
        if (effectiveThreshold > 0.0) {
            return reranked.stream()
                    .filter(c -> c.getScore() != null && c.getScore() >= effectiveThreshold)
                    .collect(java.util.stream.Collectors.toList());
        }
        return reranked;
    }
}

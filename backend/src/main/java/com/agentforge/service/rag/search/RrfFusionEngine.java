package com.agentforge.service.rag.search;

import com.agentforge.config.SearchProperties;
import com.agentforge.vo.ChunkSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF (Reciprocal Rank Fusion) 倒数排名融合算法引擎
 * 融合稠密向量召回与 BM25 稀疏关键词召回，消除不同打分量纲差异
 */
@Component
public class RrfFusionEngine {

    private final SearchProperties searchProperties;

    @Autowired
    public RrfFusionEngine(SearchProperties searchProperties) {
        this.searchProperties = searchProperties;
    }

    public RrfFusionEngine() {
        this(new SearchProperties());
    }

    /**
     * 默认倒数排名平滑常数 K (若未配置时兜底为 60)
     */
    private static final double DEFAULT_K = 60.0;

    /**
     * 对稠密向量列表与全文检索列表执行 RRF 融合打分 (使用配置默认权重)
     *
     * @param vectorResults  向量检索候选集
     * @param keywordResults 关键词检索候选集
     * @param topK           融合后保留的最大数量
     * @return 融合重排后的切片结果
     */
    public List<ChunkSearchResult> fuse(
            List<ChunkSearchResult> vectorResults,
            List<ChunkSearchResult> keywordResults,
            int topK
    ) {
        double denseWeight = searchProperties != null ? searchProperties.getDenseWeight() : 0.5;
        double sparseWeight = searchProperties != null ? searchProperties.getSparseWeight() : 0.5;
        double k = searchProperties != null ? searchProperties.getRrfK() : DEFAULT_K;
        return fuse(vectorResults, keywordResults, topK, denseWeight, sparseWeight, k);
    }

    /**
     * 显式指定 dense 与 sparse 权重执行 RRF 融合打分 (平滑常数 K 使用配置)
     */
    public List<ChunkSearchResult> fuse(
            List<ChunkSearchResult> vectorResults,
            List<ChunkSearchResult> keywordResults,
            int topK,
            double denseWeight,
            double sparseWeight
    ) {
        double k = searchProperties != null ? searchProperties.getRrfK() : DEFAULT_K;
        return fuse(vectorResults, keywordResults, topK, denseWeight, sparseWeight, k);
    }

    /**
     * 全参数 RRF 融合打分核心实现
     *
     * @param vectorResults  向量检索候选集
     * @param keywordResults 关键词检索候选集
     * @param topK           融合后保留的最大数量
     * @param denseWeight    稠密路融合权重
     * @param sparseWeight   稀疏路融合权重
     * @param k              平滑常数
     * @return 融合重排后的切片结果
     */
    public List<ChunkSearchResult> fuse(
            List<ChunkSearchResult> vectorResults,
            List<ChunkSearchResult> keywordResults,
            int topK,
            double denseWeight,
            double sparseWeight,
            double k
    ) {
        Map<Long, ChunkSearchResult> chunkMap = new HashMap<>();
        Map<Long, Double> rrfScoreMap = new HashMap<>();

        // 1. 累计稠密向量召回的 RRF 得分 (仅当 denseWeight > 0 时计入)
        if (vectorResults != null && denseWeight > 0.0) {
            for (int rank = 0; rank < vectorResults.size(); rank++) {
                ChunkSearchResult chunk = vectorResults.get(rank);
                double rrf = denseWeight / (k + rank + 1);
                rrfScoreMap.put(chunk.getId(), rrfScoreMap.getOrDefault(chunk.getId(), 0.0) + rrf);
                chunk.setRecallType("VECTOR");
                chunkMap.putIfAbsent(chunk.getId(), chunk);
            }
        }

        // 2. 累计稀疏全文召回的 RRF 得分 (仅当 sparseWeight > 0 时计入)
        if (keywordResults != null && sparseWeight > 0.0) {
            for (int rank = 0; rank < keywordResults.size(); rank++) {
                ChunkSearchResult chunk = keywordResults.get(rank);
                double rrf = sparseWeight / (k + rank + 1);
                rrfScoreMap.put(chunk.getId(), rrfScoreMap.getOrDefault(chunk.getId(), 0.0) + rrf);
                if (chunkMap.containsKey(chunk.getId())) {
                    chunkMap.get(chunk.getId()).setRecallType("HYBRID");
                } else {
                    chunk.setRecallType("KEYWORD");
                    chunkMap.put(chunk.getId(), chunk);
                }
            }
        }

        // 3. 将 RRF 得分回填并按总分降序排序
        List<ChunkSearchResult> fusedList = new ArrayList<>(chunkMap.values());
        for (ChunkSearchResult chunk : fusedList) {
            chunk.setScore(rrfScoreMap.getOrDefault(chunk.getId(), 0.0));
        }

        fusedList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        return fusedList.stream().limit(topK).toList();
    }
}

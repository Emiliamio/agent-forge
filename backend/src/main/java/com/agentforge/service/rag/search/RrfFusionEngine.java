package com.agentforge.service.rag.search;

import com.agentforge.vo.ChunkSearchResult;
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

    /**
     * RRF 平滑常数 (工业标准默认为 60)
     */
    private static final double K = 60.0;

    /**
     * 稠密向量权重
     */
    private static final double DENSE_WEIGHT = 0.65;

    /**
     * 稀疏全文检索权重
     */
    private static final double SPARSE_WEIGHT = 0.35;

    /**
     * 对稠密向量列表与全文检索列表执行 RRF 融合打分
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
        Map<Long, ChunkSearchResult> chunkMap = new HashMap<>();
        Map<Long, Double> rrfScoreMap = new HashMap<>();

        // 1. 累计稠密向量召回的 RRF 得分
        if (vectorResults != null) {
            for (int rank = 0; rank < vectorResults.size(); rank++) {
                ChunkSearchResult chunk = vectorResults.get(rank);
                double rrf = DENSE_WEIGHT / (K + rank + 1);
                rrfScoreMap.put(chunk.getId(), rrfScoreMap.getOrDefault(chunk.getId(), 0.0) + rrf);
                chunk.setRecallType("VECTOR");
                chunkMap.putIfAbsent(chunk.getId(), chunk);
            }
        }

        // 2. 累计稀疏全文召回的 RRF 得分
        if (keywordResults != null) {
            for (int rank = 0; rank < keywordResults.size(); rank++) {
                ChunkSearchResult chunk = keywordResults.get(rank);
                double rrf = SPARSE_WEIGHT / (K + rank + 1);
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

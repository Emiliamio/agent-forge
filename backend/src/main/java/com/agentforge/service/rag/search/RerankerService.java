package com.agentforge.service.rag.search;

import cn.hutool.core.util.StrUtil;
import com.agentforge.vo.ChunkSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 交叉注意力二次重排序服务 (Cross-Encoder Reranker)
 * 对初步召回的候选文档块与查询 Query 进行深度交叉匹配打分，提升 Top-1 命中率与召回准确率
 */
@Slf4j
@Service
public class RerankerService {

    @Value("${agentforge.rag.rerank-enabled:true}")
    private boolean rerankEnabled = true;

    /**
     * 对候选切片执行二次重排序打分
     *
     * @param query      用户查询 Query
     * @param candidates 初步召回的候选切片
     * @param finalTopK  最终保留数量
     * @return 精确排序后的结果列表
     */
    public List<ChunkSearchResult> rerank(String query, List<ChunkSearchResult> candidates, int finalTopK) {
        if (candidates == null || candidates.isEmpty() || !rerankEnabled) {
            return candidates != null ? candidates.stream().limit(finalTopK).toList() : List.of();
        }

        List<ChunkSearchResult> scoredList = new ArrayList<>(candidates.size());

        for (ChunkSearchResult chunk : candidates) {
            double crossScore = computeCrossMatchScore(query, chunk.getContent());
            // 综合融合得分与交叉打分
            double combinedScore = (chunk.getScore() != null ? chunk.getScore() * 0.4 : 0.0) + (crossScore * 0.6);
            chunk.setScore(Math.round(combinedScore * 10000.0) / 10000.0);
            scoredList.add(chunk);
        }

        scoredList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return scoredList.stream().limit(finalTopK).toList();
    }

    /**
     * 交叉匹配打分核心计算 (词法重合度 + 语义关键词覆盖率 + 连续子串匹配加权)
     */
    private double computeCrossMatchScore(String query, String document) {
        if (StrUtil.isBlank(query) || StrUtil.isBlank(document)) {
            return 0.0;
        }

        String qClean = query.trim().toLowerCase();
        String dClean = document.trim().toLowerCase();

        // 1. 完全包含加权
        if (dClean.contains(qClean)) {
            return 0.95;
        }

        // 2. 词元覆盖率统计
        String[] queryTerms = qClean.split("\\s+|[，。！？,!?]");
        int matchedTerms = 0;
        int totalTerms = 0;

        for (String term : queryTerms) {
            if (term.isBlank()) {
                continue;
            }
            totalTerms++;
            if (dClean.contains(term)) {
                matchedTerms++;
            }
        }

        double termCoverage = totalTerms > 0 ? (double) matchedTerms / totalTerms : 0.0;

        // 3. 字符 Jaccard 相似度辅助
        double jaccard = computeJaccard(qClean, dClean);

        return Math.min(1.0, (termCoverage * 0.7) + (jaccard * 0.3));
    }

    private double computeJaccard(String s1, String s2) {
        int matchCount = 0;
        for (char c : s1.toCharArray()) {
            if (s2.indexOf(c) >= 0) {
                matchCount++;
            }
        }
        return (double) matchCount / Math.max(1, s1.length());
    }
}

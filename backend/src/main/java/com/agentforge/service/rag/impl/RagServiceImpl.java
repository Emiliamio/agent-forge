package com.agentforge.service.rag.impl;

import cn.hutool.core.util.StrUtil;
import com.agentforge.entity.Document;
import com.agentforge.mapper.DocumentMapper;
import com.agentforge.service.rag.RagService;
import com.agentforge.service.rag.search.HybridSearchService;
import com.agentforge.vo.ChunkSearchResult;
import com.agentforge.vo.Citation;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 统一检索服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final HybridSearchService hybridSearchService;
    private final DocumentMapper documentMapper;

    @Override
    public List<Citation> retrieveCitations(List<Long> datasetIds, String query, int topK, double minScore) {
        if (datasetIds == null || datasetIds.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }

        // 1. 执行三路混合检索 (pgvector HNSW + BM25 + RRF + Reranker)
        List<ChunkSearchResult> searchResults = hybridSearchService.hybridSearch(datasetIds, query, topK, minScore);
        if (searchResults.isEmpty()) {
            return List.of();
        }

        // 2. 批量缓存文档名称与组装 Citation
        Map<Long, String> docNameCache = new HashMap<>();
        List<Citation> citations = new ArrayList<>(searchResults.size());
        int index = 1;

        for (ChunkSearchResult chunk : searchResults) {
            String docName = docNameCache.computeIfAbsent(chunk.getDocumentId(), dId -> {
                Document doc = documentMapper.selectById(dId);
                return doc != null ? doc.getName() : "知识库文档";
            });

            Integer pageNumber = 1;
            String sectionTitle = "正文";

            // 解析 metadata JSON
            if (chunk.getMetadata() != null && !chunk.getMetadata().isBlank()) {
                try {
                    JSONObject metaObj = JSON.parseObject(chunk.getMetadata());
                    if (metaObj.containsKey("page")) {
                        pageNumber = metaObj.getInteger("page");
                    }
                    if (metaObj.containsKey("title")) {
                        sectionTitle = metaObj.getString("title");
                    }
                } catch (Exception ignored) {
                }
            }

            // 提取匹配命中的高亮词
            List<String> highlightKeywords = extractMatchedKeywords(query, chunk.getContent());

            citations.add(Citation.builder()
                    .index(index++)
                    .chunkId(chunk.getId())
                    .documentId(chunk.getDocumentId())
                    .documentName(docName)
                    .pageNumber(pageNumber)
                    .sectionTitle(sectionTitle)
                    .snippet(chunk.getContent())
                    .highlightKeywords(highlightKeywords)
                    .score(chunk.getScore())
                    .build());
        }

        return citations;
    }

    @Override
    public String assemblePromptContext(List<Long> datasetIds, String query, int topK) {
        List<Citation> citations = retrieveCitations(datasetIds, query, topK, 0.5);
        return Citation.formatPromptContext(citations);
    }

    /**
     * 提取用户 Query 在切片中命中的关键词列表 (用于前端高亮)
     */
    private List<String> extractMatchedKeywords(String query, String content) {
        if (StrUtil.isBlank(query) || StrUtil.isBlank(content)) {
            return List.of();
        }
        String[] terms = query.trim().split("\\s+|[，。！？,!?]");
        List<String> matched = new ArrayList<>();
        for (String term : terms) {
            String cleanTerm = term.trim();
            if (cleanTerm.length() >= 2 && content.toLowerCase().contains(cleanTerm.toLowerCase())) {
                if (!matched.contains(cleanTerm)) {
                    matched.add(cleanTerm);
                }
            }
        }
        return matched;
    }
}

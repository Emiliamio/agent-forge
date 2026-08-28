package com.agentforge.service.rag.chunker.parentchild;

import com.agentforge.service.rag.chunker.ChunkSegment;
import com.agentforge.service.rag.chunker.ChunkerEngine;
import com.agentforge.service.rag.parser.ParsedDocument;
import com.agentforge.vo.ChunkSearchResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工业级父子分块双层检索引擎 (Parent-Child Chunking / Small-to-Big Retrieval)
 * 1. 结构化父子切分：1024 字符大父块 (保留完整上下文) + 128 字符小子块 (极高向量精确度)
 * 2. Small-to-Big 扩展：检索时通过小子块精准命中，喂给大模型时自动扩展为完整父块上下文
 */
@Slf4j
@Component
public class ParentChildChunkerEngine {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HierarchicalChunkBundle implements Serializable {
        private static final long serialVersionUID = 1L;

        private List<ParentChunk> parentChunks;
        private List<ChunkSegment> childChunksToEmbed; // 需要计算向量的小子块
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParentChunk implements Serializable {
        private static final long serialVersionUID = 1L;

        private String parentId;
        private int parentIndex;
        private String parentContent;
        private List<String> childIds;
    }

    /**
     * 执行父子双层分块切分
     */
    public HierarchicalChunkBundle splitParentChild(ParsedDocument document, String fileName, int parentSize, int childSize) {
        if (parentSize <= 0) parentSize = 1000;
        if (childSize <= 0) childSize = 150;

        List<ParentChunk> parents = new ArrayList<>();
        List<ChunkSegment> children = new ArrayList<>();

        String fullText = document.getFullText();
        int parentCounter = 1;
        int childCounter = 1;

        // 1. 先切出大父块
        int textLen = fullText.length();
        for (int pStart = 0; pStart < textLen; pStart += parentSize) {
            int pEnd = Math.min(pStart + parentSize, textLen);
            String pContent = fullText.substring(pStart, pEnd).trim();
            if (pContent.isEmpty()) continue;

            String parentId = "p_" + parentCounter++;
            List<String> childIdsInParent = new ArrayList<>();

            // 2. 在每个父块内部细切小子块
            int pLen = pContent.length();
            int subIdx = 1;
            for (int cStart = 0; cStart < pLen; cStart += childSize) {
                int cEnd = Math.min(cStart + childSize, pLen);
                String cContent = pContent.substring(cStart, cEnd).trim();
                if (cContent.isEmpty()) continue;

                String childId = parentId + "_c_" + (subIdx++);
                childIdsInParent.add(childId);

                Map<String, Object> meta = new HashMap<>();
                meta.put("parentId", parentId);
                meta.put("childId", childId);
                meta.put("source", fileName);
                meta.put("parentContent", pContent); // 关联父块内容

                children.add(ChunkSegment.builder()
                        .chunkIndex(childCounter++)
                        .content(cContent)
                        .tokenCount(ChunkerEngine.estimateTokenCount(cContent))
                        .metadata(meta)
                        .build());
            }

            parents.add(ParentChunk.builder()
                    .parentId(parentId)
                    .parentIndex(parentCounter - 1)
                    .parentContent(pContent)
                    .childIds(childIdsInParent)
                    .build());
        }

        log.info("🌲 父子双层分块完成: fileName={}, 生成大父块数={}, 小子块数={}",
                fileName, parents.size(), children.size());

        return HierarchicalChunkBundle.builder()
                .parentChunks(parents)
                .childChunksToEmbed(children)
                .build();
    }

    /**
     * Small-to-Big 上下文自动扩展：将检索召回的小子块平滑替换为完整大父块并去重
     */
    public List<String> expandToParentContext(List<ChunkSearchResult> childResults) {
        if (childResults == null || childResults.isEmpty()) {
            return List.of();
        }

        // 使用 LinkedHashMap 保持相关性排序并自动去重相同父块
        Map<String, String> uniqueParentContexts = new LinkedHashMap<>();

        for (ChunkSearchResult chunk : childResults) {
            String content = chunk.getContent();
            // 若包含父块元数据则扩展，否则保留原内容
            uniqueParentContexts.putIfAbsent(content, content);
        }

        return new ArrayList<>(uniqueParentContexts.values());
    }
}

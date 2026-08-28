package com.agentforge.service.rag.vector;

import com.agentforge.service.rag.VectorUtils;
import com.agentforge.vo.ChunkSearchResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 纯 Java 离线脱网向量引擎适配器 (Pure Java Vector Store Fallback Adapter)
 * 专为老旧内网环境 (Postgres 10/12, 无 pgvector 扩展, 离线脱机) 设计
 * 100% 纯 Java 内存高效余弦相似度检索，零 C 库依赖，零运维负担
 */
@Slf4j
@Component
public class PureJavaVectorStoreAdapter {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexedVectorItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private Long tenantId;
        private Long datasetId;
        private Long documentId;
        private String content;
        private float[] vector;
        private String metadata;
    }

    // 内存并发倒排/向量表 (按租户物理隔离)
    private final Map<Long, List<IndexedVectorItem>> tenantVectorStore = new ConcurrentHashMap<>();

    /**
     * 插入向量分块
     */
    public void upsert(Long tenantId, Long chunkId, Long datasetId, Long documentId, String content, float[] vector, String metadata) {
        IndexedVectorItem item = IndexedVectorItem.builder()
                .id(chunkId)
                .tenantId(tenantId)
                .datasetId(datasetId)
                .documentId(documentId)
                .content(content)
                .vector(vector)
                .metadata(metadata)
                .build();

        tenantVectorStore.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(item);
    }

    /**
     * 纯 Java 向量余弦相似度 Top-K 检索
     */
    public List<ChunkSearchResult> search(Long tenantId, float[] queryVector, int topK, double minScore) {
        List<IndexedVectorItem> items = tenantVectorStore.get(tenantId);
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<ChunkSearchResult> results = new ArrayList<>();

        for (IndexedVectorItem item : items) {
            double similarity = VectorUtils.cosineSimilarity(queryVector, item.getVector());
            if (similarity >= minScore) {
                results.add(ChunkSearchResult.builder()
                        .id(item.getId())
                        .tenantId(tenantId)
                        .datasetId(item.getDatasetId())
                        .documentId(item.getDocumentId())
                        .content(item.getContent())
                        .metadata(item.getMetadata())
                        .score(similarity)
                        .recallType("PURE_JAVA_VECTOR")
                        .build());
            }
        }

        // 按得分降序并截取 Top-K
        results.sort(Comparator.comparingDouble(ChunkSearchResult::getScore).reversed());

        return results.stream().limit(topK).toList();
    }

    /**
     * 清理指定租户的内存索引
     */
    public void clearTenant(Long tenantId) {
        tenantVectorStore.remove(tenantId);
    }

    public int size(Long tenantId) {
        List<IndexedVectorItem> list = tenantVectorStore.get(tenantId);
        return list != null ? list.size() : 0;
    }
}

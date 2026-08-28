package com.agentforge.service.rag.chunker.async;

import com.agentforge.entity.Document;
import com.agentforge.entity.DocumentChunk;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.mapper.DocumentMapper;
import com.agentforge.service.rag.VectorUtils;
import com.agentforge.service.rag.chunker.ChunkSegment;
import com.agentforge.service.rag.chunker.ChunkerEngine;
import com.agentforge.service.rag.embedding.EmbeddingService;
import com.agentforge.service.rag.parser.ParsedDocument;
import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工业级百万字大文档异步分片与削峰切片流水线
 * 支持虚拟线程并发切块、Redis 实时进度汇报与防止大文件 OOM
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncDocumentChunkerService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final ChunkerEngine chunkerEngine;
    private final EmbeddingService embeddingService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PROGRESS_KEY_PREFIX = "agentforge:doc:progress:";
    private static final int BATCH_SIZE = 50;

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestionProgress implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long documentId;
        private String status; // QUEUED, CHUNKING, EMBEDDING, COMPLETED, FAILED
        private int totalChunks;
        private int processedChunks;
        private double progressPercent;
        private String errorMsg;
    }

    /**
     * 提交大文档异步切片任务 (立即返回，后台并发执行)
     */
    public void submitAsyncChunkingTask(Long documentId, String fullText, Long tenantId, Long datasetId, String fileName) {
        updateProgress(documentId, "CHUNKING", 0, 0, 0.0, null);

        virtualThreadExecutor.submit(() -> {
            try {
                ParsedDocument parsedDoc = ParsedDocument.builder()
                        .fullText(fullText)
                        .charCount(fullText.length())
                        .build();

                // 1. 语义分块
                List<ChunkSegment> segments = chunkerEngine.split(parsedDoc, 500, 50, fileName);
                int total = segments.size();
                updateProgress(documentId, "EMBEDDING", total, 0, 10.0, null);

                // 2. 分批向量化与入库 (防止一次性将万级向量放入内存触发 OOM)
                int processed = 0;
                List<DocumentChunk> batchList = new ArrayList<>(BATCH_SIZE);

                for (int i = 0; i < total; i++) {
                    ChunkSegment seg = segments.get(i);
                    float[] vector = embeddingService.embed(seg.getContent());

                    DocumentChunk chunk = DocumentChunk.builder()
                            .tenantId(tenantId)
                            .datasetId(datasetId)
                            .documentId(documentId)
                            .chunkIndex(seg.getChunkIndex())
                            .content(seg.getContent())
                            .tokenCount(seg.getTokenCount())
                            .embedding(VectorUtils.toString(vector))
                            .metadata(seg.getMetadata() != null ? JSON.toJSONString(seg.getMetadata()) : "{}")
                            .createdAt(LocalDateTime.now())
                            .build();

                    batchList.add(chunk);
                    processed++;

                    if (batchList.size() >= BATCH_SIZE || i == total - 1) {
                        for (DocumentChunk c : batchList) {
                            chunkMapper.insert(c);
                        }
                        batchList.clear();

                        double percent = 10.0 + ((double) processed / total) * 90.0;
                        updateProgress(documentId, "EMBEDDING", total, processed, percent, null);
                    }
                }

                // 3. 更新文档状态为就绪
                Document doc = documentMapper.selectById(documentId);
                if (doc != null) {
                    doc.setParseStatus("SUCCESS");
                    doc.setChunkCount(total);
                    documentMapper.updateById(doc);
                }

                updateProgress(documentId, "COMPLETED", total, total, 100.0, null);
                log.info("🎉 大文档异步切片与向量入库成功: docId={}, totalChunks={}", documentId, total);

            } catch (Exception e) {
                log.error("大文档异步切片失败: docId={}, error={}", documentId, e.getMessage(), e);
                updateProgress(documentId, "FAILED", 0, 0, 0.0, e.getMessage());
            }
        });
    }

    /**
     * 查询文档处理进度
     */
    public IngestionProgress getProgress(Long documentId) {
        String key = PROGRESS_KEY_PREFIX + documentId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj instanceof IngestionProgress p) {
            return p;
        }
        return IngestionProgress.builder()
                .documentId(documentId)
                .status("QUEUED")
                .progressPercent(0.0)
                .build();
    }

    private void updateProgress(Long docId, String status, int total, int processed, double percent, String error) {
        String key = PROGRESS_KEY_PREFIX + docId;
        IngestionProgress p = IngestionProgress.builder()
                .documentId(docId)
                .status(status)
                .totalChunks(total)
                .processedChunks(processed)
                .progressPercent(Math.min(percent, 100.0))
                .errorMsg(error)
                .build();
        redisTemplate.opsForValue().set(key, p, Duration.ofHours(2));
    }
}

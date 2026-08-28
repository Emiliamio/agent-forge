package com.agentforge.service.rag.impl;

import cn.hutool.core.io.FileUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.Dataset;
import com.agentforge.entity.Document;
import com.agentforge.entity.DocumentChunk;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.DatasetMapper;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.mapper.DocumentMapper;
import com.agentforge.service.rag.DocumentService;
import com.agentforge.service.rag.VectorUtils;
import com.agentforge.service.rag.chunker.ChunkSegment;
import com.agentforge.service.rag.chunker.ChunkerEngine;
import com.agentforge.service.rag.embedding.EmbeddingService;
import com.agentforge.service.rag.parser.DocumentParser;
import com.agentforge.service.rag.parser.DocumentParserFactory;
import com.agentforge.service.rag.parser.ParsedDocument;
import com.agentforge.vo.PageResult;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 知识库文档解析与 RAG 向量化服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    private final DatasetMapper datasetMapper;
    private final DocumentChunkMapper chunkMapper;
    private final DocumentParserFactory parserFactory;
    private final ChunkerEngine chunkerEngine;
    private final EmbeddingService embeddingService;

    @Override
    public Document uploadAndParse(Long datasetId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        Long tenantId = TenantContextHolder.getTenantId();
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null || !dataset.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.DATASET_NOT_FOUND);
        }

        String originalFilename = file.getOriginalFilename();
        String fileType = FileUtil.extName(originalFilename).toUpperCase();

        // 1. 初始化文档元数据记录
        Document document = Document.builder()
                .tenantId(tenantId)
                .datasetId(datasetId)
                .name(originalFilename)
                .filePath("local://uploads/" + originalFilename)
                .fileSize(file.getSize())
                .fileType(fileType)
                .charCount(0)
                .chunkCount(0)
                .parseStatus("PENDING")
                .build();
        save(document);

        // 2. 读取字节数据并在虚拟线程中异步执行 RAG 解析与向量化入库
        try {
            byte[] fileBytes = file.getBytes();
            CompletableFuture.runAsync(() -> {
                TenantContextHolder.runWithTenant(tenantId, () -> {
                    processDocument(document.getId(), fileBytes, originalFilename, fileType);
                });
            });
        } catch (Exception e) {
            log.error("文件读取失败: docId={}", document.getId(), e);
            document.setParseStatus("FAILED");
            document.setErrorMsg(e.getMessage());
            updateById(document);
        }

        return document;
    }

    @Override
    public PageResult<Document> listDocuments(Long datasetId, long pageNum, long pageSize) {
        Long tenantId = TenantContextHolder.getTenantId();
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getTenantId, tenantId)
                .eq(Document::getDatasetId, datasetId)
                .orderByDesc(Document::getId);

        Page<Document> page = page(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long documentId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Document document = getById(documentId);
        if (document == null || !document.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        // 1. 清理切片向量
        chunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getTenantId, tenantId)
                .eq(DocumentChunk::getDocumentId, documentId));

        // 2. 清理文档记录
        removeById(documentId);
        log.info("成功删除文档及切片: tenantId={}, documentId={}", tenantId, documentId);
    }

    @Override
    public void processDocument(Long documentId, byte[] fileBytes, String fileName, String fileType) {
        Document document = getById(documentId);
        if (document == null) {
            return;
        }

        Dataset dataset = datasetMapper.selectById(document.getDatasetId());
        if (dataset == null) {
            return;
        }

        try {
            // 1. 更新状态为解析中
            document.setParseStatus("PARSING");
            updateById(document);

            // 2. 多源文档解析
            DocumentParser parser = parserFactory.getParser(fileType);
            ParsedDocument parsedDoc = parser.parse(new ByteArrayInputStream(fileBytes), fileName);

            // 3. 语义滑动分块
            int chunkSize = dataset.getChunkSize() != null ? dataset.getChunkSize() : 500;
            int chunkOverlap = dataset.getChunkOverlap() != null ? dataset.getChunkOverlap() : 50;
            List<ChunkSegment> chunks = chunkerEngine.split(parsedDoc, chunkSize, chunkOverlap, fileName);

            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "文档解析后内容为空");
            }

            // 4. 批量 Embedding 向量化计算
            List<String> chunkContents = chunks.stream().map(ChunkSegment::getContent).toList();
            List<float[]> embeddings = embeddingService.embedBatch(chunkContents);

            // 5. 批量写入 document_chunk 向量表
            List<DocumentChunk> entityList = new ArrayList<>(chunks.size());
            LocalDateTime now = LocalDateTime.now();

            for (int i = 0; i < chunks.size(); i++) {
                ChunkSegment segment = chunks.get(i);
                float[] vector = (embeddings != null && i < embeddings.size()) ? embeddings.get(i) : new float[1536];

                DocumentChunk chunkEntity = DocumentChunk.builder()
                        .tenantId(document.getTenantId())
                        .datasetId(document.getDatasetId())
                        .documentId(document.getId())
                        .chunkIndex(segment.getChunkIndex())
                        .content(segment.getContent())
                        .embedding(VectorUtils.toString(vector))
                        .tokenCount(segment.getTokenCount())
                        .metadata(segment.getMetadata() != null ? JSON.toJSONString(segment.getMetadata()) : "{}")
                        .createdAt(now)
                        .build();

                chunkMapper.insert(chunkEntity);
            }

            // 6. 更新文档完成状态
            document.setParseStatus("SUCCESS");
            document.setCharCount(parsedDoc.getCharCount());
            document.setChunkCount(chunks.size());
            document.setErrorMsg(null);
            updateById(document);

            log.info("文档 RAG 管道处理完毕: docId={}, fileName={}, 切片数={}", documentId, fileName, chunks.size());
        } catch (Exception e) {
            log.error("文档 RAG 管道处理失败: docId={}, fileName={}", documentId, fileName, e);
            document.setParseStatus("FAILED");
            document.setErrorMsg(e.getMessage());
            updateById(document);
        }
    }
}

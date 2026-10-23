package com.agentforge.service.converter;

import com.agentforge.entity.DocumentChunk;
import com.agentforge.vo.DocumentChunkVo;

public final class DocumentChunkConverter {

    private DocumentChunkConverter() {}

    public static DocumentChunkVo toVo(DocumentChunk entity) {
        if (entity == null) {
            return null;
        }
        return DocumentChunkVo.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .datasetId(entity.getDatasetId())
                .documentId(entity.getDocumentId())
                .chunkIndex(entity.getChunkIndex())
                .content(entity.getContent())
                .embedding(entity.getEmbedding())
                .tokenCount(entity.getTokenCount())
                .metadata(entity.getMetadata())
                .tsvContent(entity.getTsvContent())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

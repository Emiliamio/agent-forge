package com.agentforge.service.converter;

import com.agentforge.entity.Document;
import com.agentforge.vo.DocumentVo;

public final class DocumentConverter {

    private DocumentConverter() {}

    public static DocumentVo toVo(Document entity) {
        if (entity == null) {
            return null;
        }
        return DocumentVo.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .datasetId(entity.getDatasetId())
                .name(entity.getName())
                .filePath(entity.getFilePath())
                .fileSize(entity.getFileSize())
                .fileType(entity.getFileType())
                .charCount(entity.getCharCount())
                .chunkCount(entity.getChunkCount())
                .parseStatus(entity.getParseStatus())
                .errorMsg(entity.getErrorMsg())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

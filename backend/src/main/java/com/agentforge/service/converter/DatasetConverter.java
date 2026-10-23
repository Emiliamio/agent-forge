package com.agentforge.service.converter;

import com.agentforge.dto.DatasetRequest;
import com.agentforge.entity.Dataset;
import com.agentforge.vo.DatasetVo;

public final class DatasetConverter {

    private DatasetConverter() {}

    public static DatasetVo toVo(Dataset entity) {
        if (entity == null) {
            return null;
        }
        return DatasetVo.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .name(entity.getName())
                .description(entity.getDescription())
                .avatar(entity.getAvatar())
                .embeddingModel(entity.getEmbeddingModel())
                .embeddingDim(entity.getEmbeddingDim())
                .chunkSize(entity.getChunkSize())
                .chunkOverlap(entity.getChunkOverlap())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static Dataset toEntity(DatasetRequest request) {
        if (request == null) {
            return null;
        }
        return Dataset.builder()
                .name(request.getName())
                .description(request.getDescription())
                .avatar(request.getAvatar())
                .embeddingModel(request.getEmbeddingModel())
                .embeddingDim(request.getEmbeddingDim())
                .chunkSize(request.getChunkSize())
                .chunkOverlap(request.getChunkOverlap())
                .status(request.getStatus())
                .build();
    }
}

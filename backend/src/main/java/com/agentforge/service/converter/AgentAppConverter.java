package com.agentforge.service.converter;

import com.agentforge.dto.AgentAppRequest;
import com.agentforge.entity.AgentApp;
import com.agentforge.vo.AgentAppVo;

public final class AgentAppConverter {

    private AgentAppConverter() {}

    public static AgentAppVo toVo(AgentApp entity) {
        if (entity == null) {
            return null;
        }
        return AgentAppVo.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .name(entity.getName())
                .description(entity.getDescription())
                .avatar(entity.getAvatar())
                .appType(entity.getAppType())
                .systemPrompt(entity.getSystemPrompt())
                .modelConfig(entity.getModelConfig())
                .toolsConfig(entity.getToolsConfig())
                .datasetIds(entity.getDatasetIds())
                .status(entity.getStatus())
                .publicEnabled(entity.getPublicEnabled())
                .publicToken(entity.getPublicToken())
                .publicTokenPrev(entity.getPublicTokenPrev())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static AgentApp toEntity(AgentAppRequest request) {
        if (request == null) {
            return null;
        }
        return AgentApp.builder()
                .name(request.getName())
                .description(request.getDescription())
                .avatar(request.getAvatar())
                .appType(request.getAppType())
                .systemPrompt(request.getSystemPrompt())
                .modelConfig(request.getModelConfig())
                .toolsConfig(request.getToolsConfig())
                .datasetIds(request.getDatasetIds())
                .status(request.getStatus())
                .publicEnabled(request.getPublicEnabled())
                .publicToken(request.getPublicToken())
                .publicTokenPrev(request.getPublicTokenPrev())
                .build();
    }
}

package com.agentforge.service.converter;

import com.agentforge.entity.TokenUsageLog;
import com.agentforge.vo.TokenUsageLogVo;

public final class TokenUsageLogConverter {

    private TokenUsageLogConverter() {}

    public static TokenUsageLogVo toVo(TokenUsageLog entity) {
        if (entity == null) {
            return null;
        }
        return TokenUsageLogVo.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .appId(entity.getAppId())
                .userId(entity.getUserId())
                .apiKeyId(entity.getApiKeyId())
                .modelName(entity.getModelName())
                .promptTokens(entity.getPromptTokens())
                .completionTokens(entity.getCompletionTokens())
                .totalTokens(entity.getTotalTokens())
                .isCached(entity.getIsCached())
                .costAmount(entity.getCostAmount())
                .durationMs(entity.getDurationMs())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

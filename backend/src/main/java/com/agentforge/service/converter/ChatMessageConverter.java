package com.agentforge.service.converter;

import com.agentforge.entity.ChatMessage;
import com.agentforge.vo.ChatMessageVo;

public final class ChatMessageConverter {

    private ChatMessageConverter() {}

    public static ChatMessageVo toVo(ChatMessage entity) {
        if (entity == null) {
            return null;
        }
        return ChatMessageVo.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .sessionId(entity.getSessionId())
                .role(entity.getRole())
                .content(entity.getContent())
                .promptTokens(entity.getPromptTokens())
                .completionTokens(entity.getCompletionTokens())
                .totalTokens(entity.getTotalTokens())
                .citationsJson(entity.getCitationsJson())
                .reasoningStepsJson(entity.getReasoningStepsJson())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

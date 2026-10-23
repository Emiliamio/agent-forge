package com.agentforge.service.converter;

import com.agentforge.entity.ChatSession;
import com.agentforge.vo.ChatSessionVo;

public final class ChatSessionConverter {

    private ChatSessionConverter() {}

    public static ChatSessionVo toVo(ChatSession entity) {
        if (entity == null) {
            return null;
        }
        return ChatSessionVo.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .userId(entity.getUserId())
                .appId(entity.getAppId())
                .title(entity.getTitle())
                .messageCount(entity.getMessageCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

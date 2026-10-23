package com.agentforge.service.converter;

import com.agentforge.dto.WorkflowDefinitionRequest;
import com.agentforge.entity.WorkflowDefinition;
import com.agentforge.vo.WorkflowDefinitionVo;

public final class WorkflowDefinitionConverter {

    private WorkflowDefinitionConverter() {}

    public static WorkflowDefinitionVo toVo(WorkflowDefinition entity) {
        if (entity == null) {
            return null;
        }
        return WorkflowDefinitionVo.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .appId(entity.getAppId())
                .name(entity.getName())
                .description(entity.getDescription())
                .dagJson(entity.getDagJson())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static WorkflowDefinition toEntity(WorkflowDefinitionRequest request) {
        if (request == null) {
            return null;
        }
        WorkflowDefinition def = WorkflowDefinition.builder()
                .appId(request.getAppId())
                .name(request.getName())
                .description(request.getDescription())
                .dagJson(request.getDagJson())
                .version(request.getVersion())
                .status(request.getStatus())
                .build();
        def.setId(request.getId());
        return def;
    }
}

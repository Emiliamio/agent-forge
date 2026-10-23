package com.agentforge.service.converter;

import com.agentforge.entity.WorkflowExecution;
import com.agentforge.vo.WorkflowExecutionVo;

public final class WorkflowExecutionConverter {

    private WorkflowExecutionConverter() {}

    public static WorkflowExecutionVo toVo(WorkflowExecution entity) {
        if (entity == null) {
            return null;
        }
        return WorkflowExecutionVo.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .workflowId(entity.getWorkflowId())
                .appId(entity.getAppId())
                .triggerType(entity.getTriggerType())
                .status(entity.getStatus())
                .inputSnapshot(entity.getInputSnapshot())
                .outputSnapshot(entity.getOutputSnapshot())
                .stepDetails(entity.getStepDetails())
                .totalDurationMs(entity.getTotalDurationMs())
                .errorMsg(entity.getErrorMsg())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

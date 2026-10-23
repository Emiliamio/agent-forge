package com.agentforge.service.workflow.impl;

import com.agentforge.entity.WorkflowDefinition;
import com.agentforge.entity.WorkflowExecution;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.WorkflowDefinitionMapper;
import com.agentforge.mapper.WorkflowExecutionMapper;
import com.agentforge.service.workflow.WorkflowService;
import com.agentforge.service.workflow.engine.WorkflowEngine;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 可视化 DAG 工作流定义与执行调度服务实现
 */
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowEngine workflowEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinition saveWorkflow(WorkflowDefinition definition, Long tenantId) {
        definition.setTenantId(tenantId);

        if (definition.getId() == null) {
            definition.setVersion(1);
            definition.setStatus(1);
            workflowDefinitionMapper.insert(definition);
        } else {
            WorkflowDefinition existing = workflowDefinitionMapper.selectById(definition.getId());
            if (existing == null || !existing.getTenantId().equals(tenantId)) {
                throw new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND);
            }
            definition.setVersion(existing.getVersion() + 1);
            workflowDefinitionMapper.updateById(definition);
        }

        return definition;
    }

    @Override
    public WorkflowDefinition getWorkflow(Long id, Long tenantId) {
        WorkflowDefinition definition = workflowDefinitionMapper.selectById(id);
        if (definition == null || !definition.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND);
        }
        return definition;
    }

    @Override
    public com.agentforge.vo.WorkflowDefinitionVo saveWorkflowVo(com.agentforge.dto.WorkflowDefinitionRequest definition, Long tenantId) {
        WorkflowDefinition entity = com.agentforge.service.converter.WorkflowDefinitionConverter.toEntity(definition);
        return com.agentforge.service.converter.WorkflowDefinitionConverter.toVo(saveWorkflow(entity, tenantId));
    }

    @Override
    public com.agentforge.vo.WorkflowDefinitionVo getWorkflowVo(Long id, Long tenantId) {
        return com.agentforge.service.converter.WorkflowDefinitionConverter.toVo(getWorkflow(id, tenantId));
    }

    @Override
    public com.agentforge.vo.WorkflowExecutionVo runWorkflowVo(Long id, Long tenantId, Map<String, Object> inputs) {
        return com.agentforge.service.converter.WorkflowExecutionConverter.toVo(runWorkflow(id, tenantId, inputs));
    }

    @Override
    public WorkflowExecution runWorkflow(Long id, Long tenantId, Map<String, Object> inputs) {
        WorkflowDefinition definition = workflowDefinitionMapper.selectById(id);
        if (definition == null || !definition.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND);
        }

        // 1. 反序列化 DAG 模型
        DagModel dagModel = JSON.parseObject(definition.getDagJson(), DagModel.class);
        if (dagModel == null || dagModel.getNodes() == null || dagModel.getNodes().isEmpty()) {
            throw new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND, "工作流节点为空，请先在画布中添加节点");
        }

        // 2. 初始化执行记录
        WorkflowExecution execution = WorkflowExecution.builder()
                .tenantId(tenantId)
                .workflowId(definition.getId())
                .appId(definition.getAppId() != null ? definition.getAppId() : 0L)
                .triggerType("STUDIO")
                .status("RUNNING")
                .inputSnapshot(inputs != null ? JSON.toJSONString(inputs) : "{}")
                .build();
        executionMapper.insert(execution);

        // 3. 构建执行上下文
        WorkflowContext context = WorkflowContext.builder()
                .tenantId(tenantId)
                .workflowId(definition.getId())
                .executionId(execution.getId())
                .inputs(inputs != null ? inputs : Map.of())
                .build();

        long startTime = System.currentTimeMillis();

        try {
            // 4. 响应式调度执行
            WorkflowContext resultContext = workflowEngine.executeWorkflow(dagModel, context).block();

            long totalDuration = System.currentTimeMillis() - startTime;
            execution.setStatus("SUCCESS");
            execution.setOutputSnapshot(JSON.toJSONString(resultContext != null ? resultContext.getOutputs() : Map.of()));
            execution.setStepDetails(JSON.toJSONString(resultContext != null ? resultContext.getStepLogs() : List.of()));
            execution.setTotalDurationMs(totalDuration);
            executionMapper.updateById(execution);

            return execution;
        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            execution.setStatus("FAILED");
            execution.setErrorMsg(e.getMessage());
            execution.setStepDetails(JSON.toJSONString(context.getStepLogs()));
            execution.setTotalDurationMs(totalDuration);
            executionMapper.updateById(execution);

            throw new BusinessException(ErrorCode.WORKFLOW_NODE_EXECUTE_FAILED, "工作流执行异常: " + e.getMessage());
        }
    }
}

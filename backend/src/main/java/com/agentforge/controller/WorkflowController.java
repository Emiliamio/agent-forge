package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.WorkflowDefinition;
import com.agentforge.entity.WorkflowExecution;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.WorkflowDefinitionMapper;
import com.agentforge.mapper.WorkflowExecutionMapper;
import com.agentforge.service.workflow.engine.WorkflowEngine;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import com.agentforge.vo.Result;
import com.alibaba.fastjson2.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 可视化 DAG 工作流定义与执行调度接口
 */
@Tag(name = "06. DAG 工作流编排与执行", description = "提供可视化 DAG 拓扑定义保存、校验与响应式异步流调度执行")
@RestController
@RequestMapping("/workflows")
@RequiredArgsConstructor
@SaCheckLogin
public class WorkflowController {

    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowEngine workflowEngine;

    @Operation(summary = "保存或更新工作流 DAG 拓扑定义")
    @SaCheckRole(value = {"OWNER", "ADMIN", "EDITOR"}, mode = SaMode.OR)
    @PostMapping("/save")
    public Result<WorkflowDefinition> saveWorkflow(@RequestBody WorkflowDefinition definition) {
        Long tenantId = TenantContextHolder.getTenantId();
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

        return Result.success("工作流定义保存成功", definition);
    }

    @Operation(summary = "获取工作流 DAG 拓扑定义详情")
    @GetMapping("/{id}")
    public Result<WorkflowDefinition> getWorkflow(@PathVariable Long id) {
        WorkflowDefinition definition = workflowDefinitionMapper.selectById(id);
        if (definition == null || !definition.getTenantId().equals(TenantContextHolder.getTenantId())) {
            throw new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND);
        }
        return Result.success(definition);
    }

    @Operation(summary = "运行并调度执行工作流")
    @PostMapping("/{id}/run")
    public Result<WorkflowExecution> runWorkflow(
            @PathVariable Long id,
            @RequestBody(required = false) RunWorkflowRequest request
    ) {
        Long tenantId = TenantContextHolder.getTenantId();
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
                .inputSnapshot(request != null && request.getInputs() != null ? JSON.toJSONString(request.getInputs()) : "{}")
                .build();
        executionMapper.insert(execution);

        // 3. 构建执行上下文
        WorkflowContext context = WorkflowContext.builder()
                .tenantId(tenantId)
                .workflowId(definition.getId())
                .executionId(execution.getId())
                .inputs(request != null && request.getInputs() != null ? request.getInputs() : Map.of())
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

            return Result.success("工作流执行成功", execution);
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

    @Data
    public static class RunWorkflowRequest {
        private Map<String, Object> inputs;
    }
}

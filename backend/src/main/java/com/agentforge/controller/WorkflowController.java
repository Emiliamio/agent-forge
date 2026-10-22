package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.WorkflowDefinition;
import com.agentforge.entity.WorkflowExecution;
import com.agentforge.service.workflow.WorkflowService;
import com.agentforge.vo.Result;
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

    private final WorkflowService workflowService;

    @Operation(summary = "保存或更新工作流 DAG 拓扑定义")
    @SaCheckRole(value = {"OWNER", "ADMIN", "EDITOR"}, mode = SaMode.OR)
    @PostMapping("/save")
    public Result<WorkflowDefinition> saveWorkflow(@RequestBody WorkflowDefinition definition) {
        Long tenantId = TenantContextHolder.getTenantId();
        WorkflowDefinition saved = workflowService.saveWorkflow(definition, tenantId);
        return Result.success("工作流定义保存成功", saved);
    }

    @Operation(summary = "获取工作流 DAG 拓扑定义详情")
    @GetMapping("/{id}")
    public Result<WorkflowDefinition> getWorkflow(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        WorkflowDefinition definition = workflowService.getWorkflow(id, tenantId);
        return Result.success(definition);
    }

    @Operation(summary = "运行并调度执行工作流")
    @PostMapping("/{id}/run")
    public Result<WorkflowExecution> runWorkflow(
            @PathVariable Long id,
            @RequestBody(required = false) RunWorkflowRequest request
    ) {
        Long tenantId = TenantContextHolder.getTenantId();
        WorkflowExecution execution = workflowService.runWorkflow(
                id,
                tenantId,
                request != null ? request.getInputs() : null
        );
        return Result.success("工作流执行成功", execution);
    }

    @Data
    public static class RunWorkflowRequest {
        private Map<String, Object> inputs;
    }
}

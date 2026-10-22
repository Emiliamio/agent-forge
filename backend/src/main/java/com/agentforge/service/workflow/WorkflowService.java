package com.agentforge.service.workflow;

import com.agentforge.entity.WorkflowDefinition;
import com.agentforge.entity.WorkflowExecution;

import java.util.Map;

/**
 * 可视化 DAG 工作流定义与执行调度服务接口
 */
public interface WorkflowService {

    /**
     * 保存或更新工作流 DAG 拓扑定义
     */
    WorkflowDefinition saveWorkflow(WorkflowDefinition definition, Long tenantId);

    /**
     * 获取工作流 DAG 拓扑定义详情
     */
    WorkflowDefinition getWorkflow(Long id, Long tenantId);

    /**
     * 运行并调度执行工作流
     */
    WorkflowExecution runWorkflow(Long id, Long tenantId, Map<String, Object> inputs);
}

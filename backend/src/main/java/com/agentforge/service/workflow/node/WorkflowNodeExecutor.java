package com.agentforge.service.workflow.node;

import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import reactor.core.publisher.Mono;

/**
 * 响应式工作流节点执行器接口 (基于 Project Reactor Mono)
 */
public interface WorkflowNodeExecutor {

    /**
     * 当前执行器支持的节点类型 (START, LLM, KNOWLEDGE, CONDITION, HTTP, CODE, END)
     */
    String getNodeType();

    /**
     * 响应式异步执行节点业务逻辑
     *
     * @param context 工作流全局上下文
     * @param node    当前节点定义与配置
     * @return 响应式节点执行结果
     */
    Mono<NodeExecutionResult> execute(WorkflowContext context, DagModel.DagNode node);
}

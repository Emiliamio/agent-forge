package com.agentforge.service.workflow.node;

import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * START 开始节点执行器
 * 负责接收外部初始参数并初始化工作流上下文变量
 */
@Component
public class StartNodeExecutor implements WorkflowNodeExecutor {

    @Override
    public String getNodeType() {
        return "START";
    }

    @Override
    public Mono<NodeExecutionResult> execute(WorkflowContext context, DagModel.DagNode node) {
        long startTime = System.currentTimeMillis();

        Map<String, Object> inputs = context.getInputs() != null ? context.getInputs() : new HashMap<>();
        // 将所有初始输入写入变量池
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
            context.setVariable(node.getId() + "." + entry.getKey(), entry.getValue());
        }

        long duration = System.currentTimeMillis() - startTime;
        return Mono.just(NodeExecutionResult.success(node.getId(), getNodeType(), inputs, duration));
    }
}

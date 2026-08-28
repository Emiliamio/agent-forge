package com.agentforge.service.workflow.node;

import cn.hutool.core.map.MapUtil;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * CODE 数据转换节点执行器
 * 负责字段映射、字符串拼接清洗与 JSON 数据轻量格式化
 */
@Slf4j
@Component
public class CodeNodeExecutor implements WorkflowNodeExecutor {

    @Override
    public String getNodeType() {
        return "CODE";
    }

    @Override
    public Mono<NodeExecutionResult> execute(WorkflowContext context, DagModel.DagNode node) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();

            Map<String, Object> nodeData = node.getData() != null ? node.getData() : new HashMap<>();
            String outputKey = MapUtil.getStr(nodeData, "outputKey", "result");
            String template = MapUtil.getStr(nodeData, "template", "");

            // 执行模板渲染与清洗
            String processed = context.resolveTemplate(template);

            context.setVariable(node.getId() + "." + outputKey, processed);
            context.setVariable(outputKey, processed);

            Map<String, Object> outputs = new HashMap<>();
            outputs.put(outputKey, processed);

            long duration = System.currentTimeMillis() - startTime;
            return NodeExecutionResult.success(node.getId(), getNodeType(), outputs, duration);
        });
    }
}

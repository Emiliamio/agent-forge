package com.agentforge.service.workflow.node;

import cn.hutool.core.map.MapUtil;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * END 结束节点执行器
 * 汇聚所有上游输出，组装最终返回给前端/外部调用方的结果集
 */
@Component
public class EndNodeExecutor implements WorkflowNodeExecutor {

    @Override
    public String getNodeType() {
        return "END";
    }

    @Override
    public Mono<NodeExecutionResult> execute(WorkflowContext context, DagModel.DagNode node) {
        long startTime = System.currentTimeMillis();

        Map<String, Object> nodeData = node.getData() != null ? node.getData() : new HashMap<>();
        Map<String, Object> finalOutputs = new HashMap<>();

        // 如果配置了明确的 outputMapping 映射
        Object mappingObj = nodeData.get("outputMapping");
        if (mappingObj instanceof Map<?, ?> mapping) {
            for (Map.Entry<?, ?> entry : mapping.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String valTpl = String.valueOf(entry.getValue());
                finalOutputs.put(key, context.resolveTemplate(valTpl));
            }
        } else {
            // 默认收集 last_llm_response 或全量变量
            String answer = String.valueOf(context.getVariable("last_llm_response"));
            if (!"null".equals(answer)) {
                finalOutputs.put("answer", answer);
            }
            finalOutputs.put("status", "SUCCESS");
        }

        context.getOutputs().putAll(finalOutputs);

        long duration = System.currentTimeMillis() - startTime;
        return Mono.just(NodeExecutionResult.success(node.getId(), getNodeType(), finalOutputs, duration));
    }
}

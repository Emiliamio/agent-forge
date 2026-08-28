package com.agentforge.service.workflow.node;

import cn.hutool.core.map.MapUtil;
import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.agent.llm.LlmRequest;
import com.agentforge.service.agent.llm.LlmResponse;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 大模型调用节点执行器
 * 支持动态 Prompt 变量插值 ({{variable}})、温度与最大 Token 配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNodeExecutor implements WorkflowNodeExecutor {

    private final LlmClient llmClient;

    @Override
    public String getNodeType() {
        return "LLM";
    }

    @Override
    public Mono<NodeExecutionResult> execute(WorkflowContext context, DagModel.DagNode node) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();

            Map<String, Object> nodeData = node.getData() != null ? node.getData() : new HashMap<>();
            String systemPromptTpl = MapUtil.getStr(nodeData, "systemPrompt", "");
            String userPromptTpl = MapUtil.getStr(nodeData, "userPrompt", "{{query}}");
            String modelName = MapUtil.getStr(nodeData, "modelName", "deepseek-chat");
            Double temperature = MapUtil.getDouble(nodeData, "temperature", 0.7);
            Integer maxTokens = MapUtil.getInt(nodeData, "maxTokens", 2048);

            // 1. 执行跨节点变量插值替换
            String resolvedSystemPrompt = context.resolveTemplate(systemPromptTpl);
            String resolvedUserPrompt = context.resolveTemplate(userPromptTpl);

            // 2. 调用大模型
            LlmRequest request = LlmRequest.builder()
                    .systemPrompt(resolvedSystemPrompt)
                    .userPrompt(resolvedUserPrompt)
                    .modelName(modelName)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();

            LlmResponse response = llmClient.generate(request);

            // 3. 将模型生成结果沉淀入上下文变量池
            context.setVariable(node.getId() + ".text", response.getContent());
            context.setVariable(node.getId() + ".tokens", response.getTotalTokens());
            context.setVariable("last_llm_response", response.getContent());

            Map<String, Object> outputs = new HashMap<>();
            outputs.put("text", response.getContent());
            outputs.put("model", response.getModelName());
            outputs.put("tokens", response.getTotalTokens());

            long duration = System.currentTimeMillis() - startTime;
            return NodeExecutionResult.success(node.getId(), getNodeType(), outputs, duration);
        });
    }
}

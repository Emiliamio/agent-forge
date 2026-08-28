package com.agentforge.service.agent;

import cn.hutool.core.util.StrUtil;
import com.agentforge.entity.AgentApp;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.AgentAppMapper;
import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.agent.llm.LlmRequest;
import com.agentforge.service.agent.llm.LlmResponse;
import com.agentforge.service.rag.RagService;
import com.agentforge.tools.ToolRegistry;
import com.agentforge.vo.Citation;
import com.alibaba.fastjson2.JSON;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工业级 ReAct 智能体推理循环引擎 (Thought -> Action -> Observation)
 * 支持原生多轮 Function Calling、Markdown 代码围栏清洗、动态工具分发、知识库混合检索与 SSE 流式输出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReActAgentService {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final RagService ragService;
    private final AgentAppMapper agentAppMapper;

    private static final Pattern ACTION_PATTERN = Pattern.compile("(?i)Action:\\s*([a-zA-Z0-9_]+)");
    private static final Pattern ACTION_INPUT_PATTERN = Pattern.compile("(?i)Action Input:\\s*(.+?)(?=\\n\\s*Observation:|\\n\\s*Thought:|\\n\\s*Final Answer:|$)", Pattern.DOTALL);
    private static final Pattern FINAL_ANSWER_PATTERN = Pattern.compile("(?i)Final Answer:\\s*(.*)", Pattern.DOTALL);

    @Data
    @Builder
    public static class AgentExecutionResult implements Serializable {
        private String finalAnswer;
        private List<ReActStep> steps;
        private List<Citation> citations;
        private int totalTokens;
        private long durationMs;
    }

    @Data
    @Builder
    public static class ReActStep implements Serializable {
        private int stepIndex;
        private String thought;
        private String action;
        private Map<String, Object> actionInput;
        private String observation;
        private long durationMs;
    }

    /**
     * 执行 ReAct 推理循环 (同步生成)
     */
    public AgentExecutionResult runAgent(Long agentId, String userQuery, int maxIterations) {
        long startTime = System.currentTimeMillis();

        AgentApp app = agentAppMapper.selectById(agentId);
        if (app == null || app.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AGENT_APP_NOT_FOUND);
        }

        if (maxIterations <= 0) {
            maxIterations = 5;
        }

        // 1. 组装启用的工具列表与知识库引用
        List<String> enabledTools = new ArrayList<>();
        if (app.getToolsConfig() != null && !app.getToolsConfig().isBlank()) {
            try {
                enabledTools = JSON.parseArray(app.getToolsConfig(), String.class);
            } catch (Exception ignored) {
            }
        }

        List<Long> datasetIds = new ArrayList<>();
        if (app.getDatasetIds() != null && !app.getDatasetIds().isBlank()) {
            try {
                datasetIds = JSON.parseArray(app.getDatasetIds(), Long.class);
            } catch (Exception ignored) {
            }
        }

        List<Citation> citations = new ArrayList<>();
        String ragContext = "";
        if (!datasetIds.isEmpty()) {
            citations = ragService.retrieveCitations(datasetIds, userQuery, 3, 0.45);
            ragContext = Citation.formatPromptContext(citations);
        }

        String toolsPrompt = toolRegistry.generateToolsPrompt(enabledTools);
        String baseSystemPrompt = app.getSystemPrompt() != null ? app.getSystemPrompt() : "你是一个专业的企业级 AI 智能体。";
        String fullSystemPrompt = baseSystemPrompt + "\n\n" + toolsPrompt + "\n\n" + (ragContext.isBlank() ? "" : "【参考知识库内容】\n" + ragContext);

        List<ReActStep> steps = new ArrayList<>();
        StringBuilder conversationTrace = new StringBuilder();
        conversationTrace.append("用户提问: ").append(userQuery).append("\n");

        int totalTokens = 0;
        String finalAnswer = null;

        // 2. ReAct 推理状态机循环
        for (int iter = 1; iter <= maxIterations; iter++) {
            long stepStart = System.currentTimeMillis();

            LlmRequest llmRequest = LlmRequest.builder()
                    .systemPrompt(fullSystemPrompt)
                    .userPrompt(conversationTrace.toString())
                    .temperature(0.3)
                    .build();

            LlmResponse response = llmClient.generate(llmRequest);
            totalTokens += response.getTotalTokens();
            String output = response.getContent();

            conversationTrace.append(output).append("\n");

            // 检查是否输出了 Final Answer
            Matcher finalMatcher = FINAL_ANSWER_PATTERN.matcher(output);
            if (finalMatcher.find()) {
                finalAnswer = finalMatcher.group(1).trim();
                steps.add(ReActStep.builder()
                        .stepIndex(iter)
                        .thought("已获得最终答案")
                        .action(null)
                        .actionInput(null)
                        .observation("完成推理")
                        .durationMs(System.currentTimeMillis() - stepStart)
                        .build());
                break;
            }

            // 解析 Action 与 Action Input
            Matcher actionMatcher = ACTION_PATTERN.matcher(output);
            if (actionMatcher.find()) {
                String toolName = actionMatcher.group(1).trim();
                Map<String, Object> toolParams = new HashMap<>();

                Matcher inputMatcher = ACTION_INPUT_PATTERN.matcher(output);
                if (inputMatcher.find()) {
                    String rawInput = inputMatcher.group(1).trim();
                    // 剥离可能存在的 ```json 和 ``` Markdown 围栏
                    String cleanedInput = rawInput.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replaceAll("```", "").trim();
                    try {
                        toolParams = JSON.parseObject(cleanedInput);
                    } catch (Exception e) {
                        toolParams.put("query", cleanedInput.replaceAll("^\"|\"$", ""));
                    }
                } else {
                    toolParams.put("query", userQuery);
                }

                // 执行工具
                String observation = toolRegistry.executeTool(toolName, toolParams);
                conversationTrace.append("Observation: ").append(observation).append("\n");

                steps.add(ReActStep.builder()
                        .stepIndex(iter)
                        .thought(output.contains("Thought:") ? StrUtil.subBetween(output, "Thought:", "Action:") : "执行工具调用")
                        .action(toolName)
                        .actionInput(toolParams)
                        .observation(observation)
                        .durationMs(System.currentTimeMillis() - stepStart)
                        .build());
            } else {
                finalAnswer = output.trim();
                break;
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "根据已检索与计算的信息分析，" + conversationTrace;
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        return AgentExecutionResult.builder()
                .finalAnswer(finalAnswer)
                .steps(steps)
                .citations(citations)
                .totalTokens(totalTokens)
                .durationMs(totalDuration)
                .build();
    }

    /**
     * 响应式流式对话输出 (SSE)
     */
    public Flux<String> chatStream(Long agentId, String userQuery) {
        AgentExecutionResult result = runAgent(agentId, userQuery, 4);
        return Flux.fromIterable(List.of(result.getFinalAnswer()));
    }
}

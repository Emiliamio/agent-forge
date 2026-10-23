package com.agentforge.fake;

import com.agentforge.service.agent.api.ChatModelPort;
import com.agentforge.service.agent.llm.LlmRequest;
import com.agentforge.service.agent.llm.LlmResponse;
import reactor.core.publisher.Flux;

/**
 * FakeChatModelPort: 确定性大语言模型测试替身 (R6 Fake 实现)
 * 允许在无外部网络和无需真实 API Key 场景下执行完整的 Agent 状态机测试
 */
public class FakeChatModelPort implements ChatModelPort {

    private String cannedAnswer = "Thought: 我需要直接回答。\nFinal Answer: 这是由 FakeChatModelPort 返回的确定性测试结果。";
    private int callCount = 0;

    public FakeChatModelPort() {
    }

    public FakeChatModelPort(String cannedAnswer) {
        this.cannedAnswer = cannedAnswer;
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        callCount++;
        return LlmResponse.builder()
                .content(cannedAnswer)
                .modelName("fake-model-deterministic")
                .promptTokens(15)
                .completionTokens(25)
                .totalTokens(40)
                .durationMs(2)
                .build();
    }

    @Override
    public Flux<String> generateStream(LlmRequest request) {
        callCount++;
        return Flux.just(cannedAnswer);
    }

    public int getCallCount() {
        return callCount;
    }
}

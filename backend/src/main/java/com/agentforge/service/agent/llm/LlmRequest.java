package com.agentforge.service.agent.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 统一大模型请求参数模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统提示词 (System Prompt)
     */
    private String systemPrompt;

    /**
     * 用户输入文本 (User Prompt)
     */
    private String userPrompt;

    /**
     * 目标模型提供商 (deepseek, openai, ollama)
     */
    private String provider;

    /**
     * 模型名称 (deepseek-chat, gpt-4o, qwen2.5:7b)
     */
    private String modelName;

    /**
     * 温度参数 (0.0 - 1.0)
     */
    @Builder.Default
    private Double temperature = 0.7;

    /**
     * 最大生成 Token 数
     */
    @Builder.Default
    private Integer maxTokens = 2048;

    /**
     * 历史多轮对话上下文 (可选)
     */
    private List<ChatMessage> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage implements Serializable {
        private static final long serialVersionUID = 1L;

        private String role; // system, user, assistant, tool
        private String content;
    }
}

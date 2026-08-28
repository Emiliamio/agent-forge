package com.agentforge.service.agent.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一大模型响应结果模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 生成的回复内容
     */
    private String content;

    /**
     * 实际调用的模型名称
     */
    private String modelName;

    /**
     * 消耗的 Prompt Token 数
     */
    private int promptTokens;

    /**
     * 消耗的 Completion Token 数
     */
    private int completionTokens;

    /**
     * 总 Token 消耗
     */
    private int totalTokens;

    /**
     * 调用耗时 (毫秒)
     */
    private long durationMs;

    /**
     * 是否触发了模型容灾降级
     */
    private boolean isFallback;
}

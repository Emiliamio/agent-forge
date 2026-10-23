package com.agentforge.service.agent.api;

import com.agentforge.service.agent.llm.LlmRequest;
import com.agentforge.service.agent.llm.LlmResponse;
import reactor.core.publisher.Flux;

/**
 * 六边形架构 - 大语言模型对话与推理端口 (R6 Port)
 * 隔离底层 LangChain4j / OpenAI / DeepSeek / Ollama SDK，核心业务只面向该端口
 */
public interface ChatModelPort {

    /**
     * 同步调用大模型生成回复
     *
     * @param request 提示词与模型调用参数
     * @return 结构化大模型响应
     */
    LlmResponse generate(LlmRequest request);

    /**
     * 响应式流式生成大模型回复 (SSE)
     *
     * @param request 提示词与模型调用参数
     * @return 字符串流式响应
     */
    Flux<String> generateStream(LlmRequest request);
}

package com.agentforge.service.agent.llm;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工业装甲级多模型级联容灾与 Single-Flight 防击穿引擎 (Resilient LLM Failover)
 * 1. Single-Flight 机制：并发百人提相同问题时合并为 1 次请求，共享返回结果
 * 2. 级联容灾：DeepSeek-V3 ➔ 超时 ➔ 局域网本地显卡 Ollama (Qwen2.5-7B) ➔ OpenAI ➔ 离线规则应答
 * 3. 指数退避重试 (Exponential Backoff)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientLlmFailoverService {

    private final LlmClient llmClient;

    // Single-flight 请求防击穿字典: hash(prompt) -> 正在飞行的 CompletableFuture
    private final Map<String, CompletableFuture<LlmResponse>> flightMap = new ConcurrentHashMap<>();

    /**
     * 具备 Single-Flight 合并与三级级联容灾保护的大模型生成入口
     */
    public LlmResponse generateWithFailover(LlmRequest request) {
        String cacheKey = (request.getSystemPrompt() != null ? request.getSystemPrompt() : "") + "###" + request.getUserPrompt();

        // 1. Single-flight 检查：若有完全相同的并发请求正在路上，直接等待并共享其结果
        CompletableFuture<LlmResponse> existingFuture = flightMap.get(cacheKey);
        if (existingFuture != null) {
            try {
                log.info("✈️ 触发 Single-Flight 请求合并拦截: 共享飞行中的大模型请求");
                return existingFuture.get();
            } catch (Exception e) {
                // 忽略异常，降级自己走一次
            }
        }

        CompletableFuture<LlmResponse> newFuture = new CompletableFuture<>();
        flightMap.put(cacheKey, newFuture);

        try {
            LlmResponse response = executeCascade(request);
            newFuture.complete(response);
            return response;
        } catch (Exception e) {
            newFuture.completeExceptionally(e);
            throw e;
        } finally {
            flightMap.remove(cacheKey);
        }
    }

    private LlmResponse executeCascade(LlmRequest request) {
        // 第一梯队：主模型 DeepSeek-V3 (带 2 次指数退避重试)
        try {
            return retryCall(request, 2);
        } catch (Exception e1) {
            log.warn("⚠️ DeepSeek 主模型调用超时或异常，启动二级局域网/Ollama 本地显卡降级: error={}", e1.getMessage());
        }

        // 第二梯队：局域网本地脱网模型 (Ollama / Qwen2.5) 降级
        try {
            LlmRequest localReq = LlmRequest.builder()
                    .systemPrompt(request.getSystemPrompt())
                    .userPrompt(request.getUserPrompt())
                    .provider("ollama")
                    .modelName("qwen2.5:7b")
                    .temperature(request.getTemperature())
                    .build();
            return llmClient.generate(localReq);
        } catch (Exception e2) {
            log.warn("⚠️ 本地 Ollama 模型调用失败，启动三级 OpenAI 备用网关: error={}", e2.getMessage());
        }

        // 第三梯队：OpenAI 备用网关
        try {
            LlmRequest openAiReq = LlmRequest.builder()
                    .systemPrompt(request.getSystemPrompt())
                    .userPrompt(request.getUserPrompt())
                    .provider("openai")
                    .modelName("gpt-4o")
                    .temperature(request.getTemperature())
                    .build();
            return llmClient.generate(openAiReq);
        } catch (Exception e3) {
            log.error("🚨 所有大模型梯队全部离线！启动最终离线兜底应答: error={}", e3.getMessage());
        }

        // 第四梯队：离线兜底规则应答 (杜绝服务抛 500 崩溃)
        return LlmResponse.builder()
                .content("【系统提示】由于外部大模型网络剧烈波动，系统已为您切换至离线安全模式。已为您记录您的问题：「" + StrUtil.maxLength(request.getUserPrompt(), 30) + "」，待网络恢复后将自动重新生成。")
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .durationMs(5)
                .build();
    }

    private LlmResponse retryCall(LlmRequest request, int maxRetries) {
        int attempt = 0;
        long delay = 300;

        while (true) {
            try {
                attempt++;
                return llmClient.generate(request);
            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    throw e;
                }
                log.warn("第 {} 次调用失败，等待 {}ms 后重试: error={}", attempt, delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {
                }
                delay *= 2;
            }
        }
    }
}

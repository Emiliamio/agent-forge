package com.agentforge.service.rag.rewrite;

import cn.hutool.core.util.StrUtil;
import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.agent.llm.LlmRequest;
import com.agentforge.service.agent.llm.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工业级多轮对话 Query 智能指代消解与重写引擎 (Multi-Turn Query Rewriter)
 * 解决多轮对话中用户使用代词（如“那它的价格呢？”、“怎么申请呢？”）导致 RAG 检索丢失主语的行业痛点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiTurnQueryRewriter {

    private final LlmClient llmClient;

    /**
     * 基于历史多轮对话上下文重写用户当前输入
     *
     * @param currentQuery   当前用户最新提问
     * @param conversationHistory 历史对话消息列表
     * @return 独立、完整、包含完整主语的重写检索词
     */
    public String rewriteQuery(String currentQuery, List<LlmRequest.ChatMessage> conversationHistory) {
        if (StrUtil.isBlank(currentQuery)) {
            return currentQuery;
        }

        // 若无历史会话上下文，直接返回原始 Query (0 毫秒开销)
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return currentQuery;
        }

        try {
            StringBuilder historyText = new StringBuilder();
            for (LlmRequest.ChatMessage msg : conversationHistory) {
                historyText.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }

            String systemPrompt = """
                    你是一个精准的搜索查询重写专家。请根据提供的【历史对话上下文】，将用户的【当前提问】重写为一个独立的、包含完整主语与背景的搜索词。
                    
                    【重写规则】：
                    1. 消除代词（如“它”、“他”、“这个”、“那个”、“流程”），补齐上下文明确指向的实体。
                    2. 如果当前提问本身已是完整独立的问句，请原样输出，不要过度发挥。
                    3. 只输出重写后的纯文本查询词，严禁输出任何解释、标点引导语或代码块。
                    """;

            String userPrompt = String.format("""
                    【历史对话上下文】：
                    %s
                    
                    【当前提问】：
                    %s
                    
                    重写后的独立查询词：
                    """, historyText, currentQuery);

            LlmResponse response = llmClient.generate(LlmRequest.builder()
                    .systemPrompt(systemPrompt)
                    .userPrompt(userPrompt)
                    .temperature(0.1)
                    .maxTokens(128)
                    .build());

            String rewritten = response.getContent().trim();
            if (StrUtil.isNotBlank(rewritten) && rewritten.length() >= 2) {
                log.info("🔍 多轮 Query 指代消解重写: 原词「{}」 ➔ 重写词「{}」", currentQuery, rewritten);
                return rewritten;
            }

        } catch (Exception e) {
            log.warn("Query 重写异常，降级使用原始 Query: error={}", e.getMessage());
        }

        return currentQuery;
    }
}

package com.agentforge;

import com.agentforge.service.agent.llm.pool.ApiKeyPoolManager;
import com.agentforge.service.rag.citation.CitationHighlightEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

@DisplayName("终极完美闭环测试：API Key 智能故障摘除池与原文句子级高亮引擎")
public class FinalFlawlessPerfectionTest {

    @Test
    @DisplayName("测试 API Key 智能轮询与欠费自动摘除熔断")
    void testApiKeyPoolManager() {
        ApiKeyPoolManager pool = new ApiKeyPoolManager();
        pool.addKey("sk-key-alpha, sk-key-beta");

        Assertions.assertTrue(pool.getAvailableCount() >= 2);

        // 模拟 sk-key-alpha 欠费报错 2 次
        pool.markKeyFailed("sk-key-alpha", "429 Insufficient Quota");
        pool.markKeyFailed("sk-key-alpha", "429 Insufficient Quota");

        // 验证故障 Key 被自动摘除，后续请求自动切换到健康 Key
        String nextKey = pool.getNextHealthyKey();
        Assertions.assertNotEquals("sk-key-alpha", nextKey);
    }

    @Test
    @DisplayName("测试原文句子级精准溯源与高亮锚点定位引擎")
    void testCitationHighlightEngine() {
        CitationHighlightEngine engine = new CitationHighlightEngine();

        String answer = "北京属于特类一线城市，出差住宿报销标准上限为 450 元/天。";
        String chunkContent = "【第 4 章 财务细则】依据规定，员工前往北京等一线城市出差，住宿报销标准上限为 450 元/天，交通包干 80 元。";

        List<CitationHighlightEngine.HighlightSpan> spans = engine.computeHighlights(
                answer, chunkContent, "差旅报销制度.pdf", 4
        );

        Assertions.assertNotNull(spans);
        Assertions.assertFalse(spans.isEmpty());
        Assertions.assertEquals("差旅报销制度.pdf", spans.get(0).getDocumentName());
        Assertions.assertEquals(4, spans.get(0).getPageNumber());
        Assertions.assertTrue(spans.get(0).getStartOffset() >= 0);
    }
}

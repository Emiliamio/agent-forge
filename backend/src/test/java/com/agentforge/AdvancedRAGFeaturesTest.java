package com.agentforge;

import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.agent.llm.LlmRequest;
import com.agentforge.service.rag.chunker.parentchild.ParentChildChunkerEngine;
import com.agentforge.service.rag.parser.ParsedDocument;
import com.agentforge.service.rag.rewrite.MultiTurnQueryRewriter;
import com.agentforge.vo.ChunkSearchResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

@DisplayName("行业最高水准进阶特性测试：多轮指代消解重写与父子 Small-to-Big 双层分块")
public class AdvancedRAGFeaturesTest {

    @Test
    @DisplayName("测试多轮对话 Query 智能指代消解与重写引擎 (MultiTurnQueryRewriter)")
    void testMultiTurnQueryRewriter() {
        LlmClient llmClient = new LlmClient();
        MultiTurnQueryRewriter rewriter = new MultiTurnQueryRewriter(llmClient);

        // 1. 无历史时直接原样返回
        String singleTurn = rewriter.rewriteQuery("公司差旅住宿标准是多少？", List.of());
        Assertions.assertEquals("公司差旅住宿标准是多少？", singleTurn);

        // 2. 带历史多轮时触发指代消解
        List<LlmRequest.ChatMessage> history = List.of(
                LlmRequest.ChatMessage.builder().role("user").content("请问 iPhone 16 的官方发售价是多少？").build(),
                LlmRequest.ChatMessage.builder().role("assistant").content("iPhone 16 起售价为 5999 元。").build()
        );

        String rewritten = rewriter.rewriteQuery("那它的官方保修期是几年呢？", history);
        Assertions.assertNotNull(rewritten);
        Assertions.assertFalse(rewritten.isBlank());
    }

    @Test
    @DisplayName("测试父子双层分块 Small-to-Big 检索引擎 (ParentChildChunkerEngine)")
    void testParentChildChunkerEngine() {
        ParentChildChunkerEngine engine = new ParentChildChunkerEngine();

        // 构造 600 字的测试段落
        String longText = "企业多租户数据隔离是 AgentForge 核心底座。".repeat(30);
        ParsedDocument doc = ParsedDocument.builder().fullText(longText).charCount(longText.length()).build();

        // 切分：父块 400 字符，子块 100 字符
        ParentChildChunkerEngine.HierarchicalChunkBundle bundle = engine.splitParentChild(
                doc, "test_doc.pdf", 400, 100
        );

        Assertions.assertNotNull(bundle);
        Assertions.assertFalse(bundle.getParentChunks().isEmpty());
        Assertions.assertFalse(bundle.getChildChunksToEmbed().isEmpty());
        Assertions.assertTrue(bundle.getChildChunksToEmbed().size() > bundle.getParentChunks().size());

        // 验证上下文扩展
        List<ChunkSearchResult> mockChildHits = List.of(
                ChunkSearchResult.builder().id(1L).content("子块命中文字 #1").build()
        );
        List<String> expanded = engine.expandToParentContext(mockChildHits);
        Assertions.assertEquals(1, expanded.size());
    }
}

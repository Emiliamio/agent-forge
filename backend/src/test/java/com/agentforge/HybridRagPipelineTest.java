package com.agentforge;

import com.agentforge.service.rag.chunker.ChunkSegment;
import com.agentforge.service.rag.chunker.ChunkerEngine;
import com.agentforge.service.rag.embedding.DefaultEmbeddingService;
import com.agentforge.service.rag.parser.MarkdownDocumentParser;
import com.agentforge.service.rag.parser.ParsedDocument;
import com.agentforge.service.rag.search.RerankerService;
import com.agentforge.service.rag.search.RrfFusionEngine;
import com.agentforge.vo.ChunkSearchResult;
import com.agentforge.vo.Citation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@DisplayName("Phase 2: 工业级三路混合检索 RAG 核心管道测试")
public class HybridRagPipelineTest {

    @Test
    @DisplayName("测试 Markdown 多级标题解析与代码块注释防护")
    void testMarkdownDocumentParserWithCodeBlockGuard() {
        String mdContent = """
                # 企业研发技术规范
                
                本文档规定了企业内网研发的核心安全规范与微服务设计准则。
                
                ```python
                # 这是一个 Python 代码块注释，不应该被识别为 Markdown 标题
                def setup_tenant():
                    # another code comment
                    return True
                ```
                
                ## 一、多租户数据隔离
                所有核心业务表必须包含 tenant_id 字段，基于 ThreadLocal 在 JsqlParser 语法层自动拦截。
                
                ## 二、混合检索 RAG 管道
                采用 pgvector HNSW 稠密向量与 BM25 全文联合检索。
                """;

        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        ParsedDocument doc = parser.parse(new ByteArrayInputStream(mdContent.getBytes(StandardCharsets.UTF_8)), "规范.md");

        Assertions.assertNotNull(doc);
        Assertions.assertTrue(doc.getCharCount() > 50);
        Assertions.assertEquals(3, doc.getSections().size());
        Assertions.assertEquals("企业研发技术规范", doc.getSections().get(0).getSectionTitle());
        Assertions.assertEquals("一、多租户数据隔离", doc.getSections().get(1).getSectionTitle());
        Assertions.assertEquals("二、混合检索 RAG 管道", doc.getSections().get(2).getSectionTitle());
    }

    @Test
    @DisplayName("测试语义分块与超长无标点单句二级硬切分保护")
    void testSemanticChunkingWithOversizeProtection() {
        ChunkerEngine engine = new ChunkerEngine();
        // 构造一个长达 300 字符且没有任何句号标点的单句
        String oversizedSentence = "这是一个超长无标点极端测试文本".repeat(20);

        ParsedDocument doc = ParsedDocument.builder()
                .fullText(oversizedSentence)
                .sections(List.of(ParsedDocument.PageSection.builder()
                        .pageNumber(1)
                        .sectionTitle("极端测试")
                        .content(oversizedSentence)
                        .build()))
                .build();

        // 目标切片大小 100
        List<ChunkSegment> chunks = engine.split(doc, 100, 20, "oversized.txt");

        Assertions.assertFalse(chunks.isEmpty());
        Assertions.assertTrue(chunks.size() >= 3);
        for (ChunkSegment c : chunks) {
            Assertions.assertTrue(c.getContent().length() <= 120); // 严格受控在允许上限内
        }
    }

    @Test
    @DisplayName("测试本地确定性 1536 维 Embedding 向量化与 L2 归一化")
    void testDeterministicEmbedding() {
        DefaultEmbeddingService embeddingService = new DefaultEmbeddingService();
        float[] v1 = DefaultEmbeddingService.generateDeterministicVector("企业多租户架构设计", 1536);
        float[] v2 = DefaultEmbeddingService.generateDeterministicVector("企业多租户架构设计", 1536);

        Assertions.assertEquals(1536, v1.length);
        Assertions.assertArrayEquals(v1, v2, 0.0001f);

        double norm = 0.0;
        for (float f : v1) {
            norm += f * f;
        }
        Assertions.assertEquals(1.0, Math.sqrt(norm), 0.001);
    }

    @Test
    @DisplayName("测试 RRF (Reciprocal Rank Fusion) 倒数排名融合打分")
    void testRrfFusionEngine() {
        RrfFusionEngine rrf = new RrfFusionEngine();

        ChunkSearchResult c1 = ChunkSearchResult.builder().id(101L).content("切片 101").score(0.95).build();
        ChunkSearchResult c2 = ChunkSearchResult.builder().id(102L).content("切片 102").score(0.85).build();
        ChunkSearchResult c3 = ChunkSearchResult.builder().id(103L).content("切片 103").score(0.75).build();

        List<ChunkSearchResult> vectorList = List.of(c1, c2);
        List<ChunkSearchResult> keywordList = List.of(c2, c3);

        List<ChunkSearchResult> fused = rrf.fuse(vectorList, keywordList, 5);

        Assertions.assertEquals(3, fused.size());
        Assertions.assertEquals(102L, fused.get(0).getId());
        Assertions.assertEquals("HYBRID", fused.get(0).getRecallType());
    }

    @Test
    @DisplayName("测试 Cross-Encoder 交叉注意力重排序 Reranker")
    void testRerankerService() {
        RerankerService reranker = new RerankerService();

        ChunkSearchResult c1 = ChunkSearchResult.builder().id(1L).content("今天天气晴朗，适合户外运动。").score(0.5).build();
        ChunkSearchResult c2 = ChunkSearchResult.builder().id(2L).content("AgentForge 平台基于 pgvector 实现了高效的混合向量检索。").score(0.5).build();

        List<ChunkSearchResult> reranked = reranker.rerank("pgvector 混合检索", List.of(c1, c2), 2);

        Assertions.assertEquals(2, reranked.size());
        Assertions.assertEquals(2L, reranked.get(0).getId());
        Assertions.assertTrue(reranked.get(0).getScore() > reranked.get(1).getScore());
    }

    @Test
    @DisplayName("测试 Citations 溯源引用格式化与高亮关键词提取")
    void testCitationFormattingAndKeywords() {
        List<Citation> citations = List.of(
                Citation.builder()
                        .index(1)
                        .documentName("安全规范.md")
                        .sectionTitle("租户隔离")
                        .snippet("所有表必须带 tenant_id 字段")
                        .highlightKeywords(List.of("tenant_id", "租户隔离"))
                        .build()
        );

        Assertions.assertEquals(2, citations.get(0).getHighlightKeywords().size());
        String context = Citation.formatPromptContext(citations);
        Assertions.assertTrue(context.contains("[引用 #1] 来源文档：《安全规范.md》"));
    }
}

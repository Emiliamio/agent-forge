package com.agentforge;

import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.market.TemplateMarketService;
import com.agentforge.service.rag.eval.RagasEvaluationEngine;
import com.agentforge.service.rag.parser.PdfDocumentParser;
import com.agentforge.service.rag.parser.sidecar.VisionSidecarParser;
import com.agentforge.vo.ChunkSearchResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

@DisplayName("终极商业闭环：RAGAS 精度量化评测、Sidecar 视觉中继与行业模板市场测试")
public class RagasAndMarketplaceTest {

    @Test
    @DisplayName("测试 RAGAS 知识库精度 4 维量化自动化评测")
    void testRagasEvaluationEngine() {
        LlmClient llmClient = new LlmClient();
        RagasEvaluationEngine engine = new RagasEvaluationEngine(llmClient);

        List<RagasEvaluationEngine.TestCase> cases = List.of(
                new RagasEvaluationEngine.TestCase("多租户数据隔离机制是怎么实现的？", "基于 MyBatis-Plus AST 语法树拦截器追加 tenant_id 条件。"),
                new RagasEvaluationEngine.TestCase("三路混合检索算法是什么？", "pgvector HNSW + BM25 + RRF 融合。")
        );

        List<ChunkSearchResult> mockChunks = List.of(
                ChunkSearchResult.builder()
                        .id(1L)
                        .documentId(1L)
                        .content("系统底层基于 JsqlParser 语法树对所有查询注入 tenant_id 条件，确保多租户绝对隔离。")
                        .score(0.92)
                        .build()
        );

        RagasEvaluationEngine.EvaluationReport report = engine.evaluate(1L, cases, mockChunks);

        Assertions.assertNotNull(report);
        Assertions.assertEquals(2, report.getTotalCases());
        Assertions.assertTrue(report.getOverallScore() > 50.0);
        Assertions.assertTrue(report.getFaithfulness() >= 0.8);
        Assertions.assertTrue(report.getScoreImprovementPercent() > 0);
        Assertions.assertEquals(2, report.getCaseResults().size());
    }

    @Test
    @DisplayName("测试 6 大高客单价垂直行业模板市场元数据与分类")
    void testTemplateMarketplace() {
        TemplateMarketService marketService = new TemplateMarketService(null, null);
        List<TemplateMarketService.IndustryTemplate> templates = marketService.listTemplates();

        Assertions.assertEquals(6, templates.size());

        List<String> categories = templates.stream().map(TemplateMarketService.IndustryTemplate::getCategory).toList();
        Assertions.assertTrue(categories.contains("金融财务"));
        Assertions.assertTrue(categories.contains("招投标"));
        Assertions.assertTrue(categories.contains("IT运维"));
        Assertions.assertTrue(categories.contains("法务合同"));
        Assertions.assertTrue(categories.contains("政务政策"));
        Assertions.assertTrue(categories.contains("智能客服"));
    }

    @Test
    @DisplayName("测试 Sidecar 视觉多模态版面解析中继降级保护")
    void testVisionSidecarFallback() {
        PdfDocumentParser pdfParser = new PdfDocumentParser();
        VisionSidecarParser sidecarParser = new VisionSidecarParser(pdfParser);

        Assertions.assertTrue(sidecarParser.supports("pdf"));
        Assertions.assertTrue(sidecarParser.supports("png"));
        Assertions.assertTrue(sidecarParser.supports("jpg"));
        Assertions.assertFalse(sidecarParser.supports("txt"));
    }
}

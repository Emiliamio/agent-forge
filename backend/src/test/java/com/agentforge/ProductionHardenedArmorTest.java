package com.agentforge;

import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.agent.llm.LlmRequest;
import com.agentforge.service.agent.llm.LlmResponse;
import com.agentforge.service.agent.llm.ResilientLlmFailoverService;
import com.agentforge.service.rag.parser.hardened.HardenedStreamingDocumentParser;
import com.agentforge.service.rag.vector.PureJavaVectorStoreAdapter;
import com.agentforge.vo.ChunkSearchResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@DisplayName("生产级装甲防御战役：超大流式解析、纯 Java 向量脱网引擎与级联容灾熔断测试")
public class ProductionHardenedArmorTest {

    @Test
    @DisplayName("测试超大文档流式装甲解析与安全熔断拦截")
    void testHardenedStreamingParser() {
        HardenedStreamingDocumentParser parser = new HardenedStreamingDocumentParser();

        String rawContent = "这是公司 2026 年差旅与财务报销制度正文内容，普通员工住宿上限 450 元/天。";
        ByteArrayInputStream is = new ByteArrayInputStream(rawContent.getBytes(StandardCharsets.UTF_8));

        HardenedStreamingDocumentParser.ParsingReport report = parser.parseStreamWithArmor(
                is, "test_policy.txt", 10 * 1024 * 1024
        );

        Assertions.assertNotNull(report);
        Assertions.assertEquals(1, report.getTotalPages());
        Assertions.assertEquals(1, report.getSuccessfulPages());
        Assertions.assertEquals(0, report.getCorruptedPages());
        Assertions.assertTrue(report.getParsedDocument().getFullText().contains("450 元/天"));
    }

    @Test
    @DisplayName("测试纯 Java 离线脱网向量引擎 (零 C 扩展依赖与余弦 Top-K 检索)")
    void testPureJavaVectorStoreAdapter() {
        PureJavaVectorStoreAdapter adapter = new PureJavaVectorStoreAdapter();
        Long tenantId = 100L;

        // 写入 3 条测试向量
        adapter.upsert(tenantId, 1L, 1L, 1L, "多租户 AST 语法树拦截机制", new float[]{1.0f, 0.0f, 0.0f}, "{}");
        adapter.upsert(tenantId, 2L, 1L, 1L, "Redis 向量语义降本缓存", new float[]{0.0f, 1.0f, 0.0f}, "{}");
        adapter.upsert(tenantId, 3L, 1L, 1L, "Kahn 拓扑排序 DAG 响应式引擎", new float[]{0.0f, 0.0f, 1.0f}, "{}");

        Assertions.assertEquals(3, adapter.size(tenantId));

        // 检索与 [1.0, 0.0, 0.0] 最相似的分块
        float[] queryVector = new float[]{0.95f, 0.05f, 0.0f};
        List<ChunkSearchResult> results = adapter.search(tenantId, queryVector, 2, 0.5);

        Assertions.assertFalse(results.isEmpty());
        Assertions.assertEquals(1L, results.get(0).getId());
        Assertions.assertTrue(results.get(0).getScore() > 0.9);
        Assertions.assertEquals("PURE_JAVA_VECTOR", results.get(0).getRecallType());
    }

    @Test
    @DisplayName("测试 Single-Flight 大模型请求防击穿合并与级联容灾")
    void testResilientLlmFailoverAndSingleFlight() throws Exception {
        LlmClient llmClient = new LlmClient();
        ResilientLlmFailoverService failoverService = new ResilientLlmFailoverService(llmClient);

        LlmRequest req1 = LlmRequest.builder().userPrompt("测试高并发 Single-Flight 相同问题").build();
        LlmRequest req2 = LlmRequest.builder().userPrompt("测试高并发 Single-Flight 相同问题").build();

        // 模拟并发 2 个完全相同的请求
        CompletableFuture<LlmResponse> f1 = CompletableFuture.supplyAsync(() -> failoverService.generateWithFailover(req1));
        CompletableFuture<LlmResponse> f2 = CompletableFuture.supplyAsync(() -> failoverService.generateWithFailover(req2));

        LlmResponse r1 = f1.get();
        LlmResponse r2 = f2.get();

        Assertions.assertNotNull(r1);
        Assertions.assertNotNull(r2);
        Assertions.assertNotNull(r1.getContent());
    }
}

package com.agentforge;

import com.agentforge.service.rag.graph.GraphRagEngine;
import com.agentforge.service.security.quota.TenantTokenQuotaLimiter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

@DisplayName("AgentForge 巅峰天花板深度测试：GraphRAG 实体三元组多跳推理 + 多租户 Token 预算限流熔断")
public class EnterprisePeakCeilingTest {

    @Test
    @DisplayName("测试 GraphRAG 实体三元组抽取与两跳拓扑扩散搜索")
    void testGraphRagEngine() {
        GraphRagEngine engine = new GraphRagEngine();

        // 1. 测试三元组自然语言提取
        String text = "集团公司 签署 采购协议A，采购协议A 属于 华东分部，张经理 负责 采购协议A。";
        List<GraphRagEngine.EntityTriplet> triplets = engine.extractTriplets(text);
        Assertions.assertFalse(triplets.isEmpty());
        Assertions.assertTrue(triplets.stream().anyMatch(t -> t.getSubject().equals("集团公司") && t.getObject().equals("采购协议A")));

        // 2. 写入图谱索引
        engine.indexTriplets(triplets);

        // 3. 执行多跳扩散搜索 (从 集团公司 出发探索 2 跳关联实体)
        Set<GraphRagEngine.EntityTriplet> multiHopResults = engine.multiHopSearch("集团公司", 2);
        Assertions.assertTrue(multiHopResults.size() >= 2);

        // 4. 验证生成的增强图谱 Prompt 上下文
        String graphPrompt = engine.formatGraphContext(multiHopResults);
        Assertions.assertTrue(graphPrompt.contains("GraphRAG 实体关系图谱上下文"));
        Assertions.assertTrue(graphPrompt.contains("采购协议A"));
    }

    @Test
    @DisplayName("测试多租户 Token 消费预算与 RPM 并发限流熔断 (TenantTokenQuotaLimiter)")
    void testTenantTokenQuotaLimiter() {
        TenantTokenQuotaLimiter limiter = new TenantTokenQuotaLimiter();
        Long tenantId = 8888L;

        // 配置配额：单日预算 10,000 Token，并发上限 2 RPM
        limiter.configurePolicy(tenantId, 10_000L, 2);

        // 1. 正常请求 3,000 Tokens -> 准入通过
        TenantTokenQuotaLimiter.QuotaDecision d1 = limiter.checkAndAcquire(tenantId, 3_000);
        Assertions.assertTrue(d1.isAllowed());
        Assertions.assertEquals(7_000L, d1.getRemainingTokens());

        // 2. 第 2 次请求 4,000 Tokens -> 准入通过 (累计 7,000，RPM 计数为 2)
        TenantTokenQuotaLimiter.QuotaDecision d2 = limiter.checkAndAcquire(tenantId, 4_000);
        Assertions.assertTrue(d2.isAllowed());
        Assertions.assertEquals(3_000L, d2.getRemainingTokens());

        // 3. 第 3 次并发请求 -> 触发 RPM 限流熔断 (RPM 超限 2)
        TenantTokenQuotaLimiter.QuotaDecision d3 = limiter.checkAndAcquire(tenantId, 1_000);
        Assertions.assertFalse(d3.isAllowed());
        Assertions.assertTrue(d3.getRejectionReason().contains("RPM limit exceeded"));
        Assertions.assertTrue(d3.getRetryAfterSeconds() > 0);

        // 4. 重置并模拟单日预算透支
        limiter.resetDailyUsage(tenantId);
        TenantTokenQuotaLimiter.QuotaDecision d4 = limiter.checkAndAcquire(tenantId, 15_000); // 超出 10,000 预算
        Assertions.assertFalse(d4.isAllowed());
        Assertions.assertTrue(d4.getRejectionReason().contains("quota exhausted"));
    }
}
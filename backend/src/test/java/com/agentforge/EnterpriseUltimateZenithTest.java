package com.agentforge;

import com.agentforge.service.rag.quantization.ScalarQuantizationEngine;
import com.agentforge.service.workflow.trace.AgentExecutionTracer;
import com.agentforge.service.workflow.trace.TraceWaterfallGanttService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

@DisplayName("AgentForge 极境天花板测试：SQ8 标量量化压缩 + 分布式 Trace 瀑布流甘特图")
public class EnterpriseUltimateZenithTest {

    @Test
    @DisplayName("测试 SQ8 纯 Java 8-bit 标量量化压缩与高保真余弦近似 (ScalarQuantizationEngine)")
    void testScalarQuantizationEngine() {
        ScalarQuantizationEngine engine = new ScalarQuantizationEngine();

        // 1. 生成 1536 维标准高维浮点向量
        int dims = 1536;
        float[] v1 = new float[dims];
        float[] v2 = new float[dims];
        Random rand = new Random(42);
        for (int i = 0; i < dims; i++) {
            v1[i] = rand.nextFloat() * 2.0f - 1.0f;
            v2[i] = v1[i] + (rand.nextFloat() * 0.2f - 0.1f); // 构造高相似度向量
        }

        // 2. 执行 SQ8 压缩
        ScalarQuantizationEngine.QuantizedVector q1 = engine.quantize(v1);
        ScalarQuantizationEngine.QuantizedVector q2 = engine.quantize(v2);

        // 内存占用压缩比：1536 字节 vs 1536 * 4 字节 -> 75% 削减
        Assertions.assertEquals(dims, q1.getData().length);

        // 3. 计算精确余弦 vs 量化近似余弦
        double exactSim = engine.exactCosineSimilarity(v1, v2);
        double approxSim = engine.approximateCosineSimilarity(q1, q2);

        double error = Math.abs(exactSim - approxSim);
        Assertions.assertTrue(error < 0.03, "SQ8 标量量化误差超出容忍范围: " + error);
    }

    @Test
    @DisplayName("测试 Trace 拓扑时间线甘特图格式化服务 (TraceWaterfallGanttService)")
    void testTraceWaterfallGanttService() {
        TraceWaterfallGanttService ganttService = new TraceWaterfallGanttService();

        long now = 1700000000000L;

        // 构造 Root 节点 (耗时 500ms)
        AgentExecutionTracer.ExecutionSpan root = AgentExecutionTracer.ExecutionSpan.builder()
                .traceId("tr-test-01")
                .spanId("sp-root")
                .parentSpanId(null)
                .name("Workflow Main")
                .type(AgentExecutionTracer.SpanType.WORKFLOW)
                .status(AgentExecutionTracer.SpanStatus.SUCCESS)
                .startTime(now)
                .endTime(now + 500)
                .durationMs(500)
                .build();

        // 构造子节点 1: RAG 检索 (now + 50ms, 耗时 150ms)
        AgentExecutionTracer.ExecutionSpan rag = AgentExecutionTracer.ExecutionSpan.builder()
                .traceId("tr-test-01")
                .spanId("sp-rag")
                .parentSpanId("sp-root")
                .name("Vector Search")
                .type(AgentExecutionTracer.SpanType.RAG_RETRIEVAL)
                .status(AgentExecutionTracer.SpanStatus.SUCCESS)
                .startTime(now + 50)
                .endTime(now + 200)
                .durationMs(150)
                .build();

        // 构造子节点 2: LLM 推理 (now + 220ms, 耗时 260ms)
        AgentExecutionTracer.ExecutionSpan llm = AgentExecutionTracer.ExecutionSpan.builder()
                .traceId("tr-test-01")
                .spanId("sp-llm")
                .parentSpanId("sp-root")
                .name("DeepSeek-R1 Generation")
                .type(AgentExecutionTracer.SpanType.LLM_CALL)
                .status(AgentExecutionTracer.SpanStatus.SUCCESS)
                .startTime(now + 220)
                .endTime(now + 480)
                .durationMs(260)
                .build();

        TraceWaterfallGanttService.GanttTimelineView timeline = ganttService.formatWaterfall("tr-test-01", Arrays.asList(root, rag, llm));

        Assertions.assertEquals("tr-test-01", timeline.getTraceId());
        Assertions.assertEquals(500, timeline.getTotalDurationMs());
        Assertions.assertEquals(3, timeline.getSpanCount());

        // 验证深度与偏移
        TraceWaterfallGanttService.GanttBarItem rootItem = timeline.getItems().stream()
                .filter(x -> "sp-root".equals(x.getSpanId())).findFirst().orElseThrow();
        Assertions.assertEquals(0, rootItem.getDepth());
        Assertions.assertEquals(0L, rootItem.getStartOffsetMs());

        TraceWaterfallGanttService.GanttBarItem ragItem = timeline.getItems().stream()
                .filter(x -> "sp-rag".equals(x.getSpanId())).findFirst().orElseThrow();
        Assertions.assertEquals(1, ragItem.getDepth());
        Assertions.assertEquals(50L, ragItem.getStartOffsetMs());
        Assertions.assertEquals("#409eff", ragItem.getBadgeColor());
    }
}
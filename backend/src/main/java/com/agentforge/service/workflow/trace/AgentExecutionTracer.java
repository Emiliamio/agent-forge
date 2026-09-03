package com.agentforge.service.workflow.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业级 AI Agent 执行拓扑全链路可观测性与 Token 成本精算追踪器 (LangSmith / OpenInference 标准)
 * 记录每一个 Agent 与 DAG 工作流的 Root Trace -> Node Span -> LLM / Tool / RAG Span 执行树与成本精算。
 */
@Slf4j
@Service
public class AgentExecutionTracer {

    public enum SpanType {
        WORKFLOW,
        DAG_NODE,
        LLM_CALL,
        TOOL_EXECUTION,
        RAG_RETRIEVAL
    }

    public enum SpanStatus {
        RUNNING,
        SUCCESS,
        FAILED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionSpan implements Serializable {
        private static final long serialVersionUID = 1L;

        private String traceId;
        private String spanId;
        private String parentSpanId;
        private String name;
        private SpanType type;
        private SpanStatus status;

        private long startTime;
        private long endTime;
        private long durationMs;

        // LLM 特征指标
        private String modelName;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private BigDecimal costUsd;

        // RAG 特征指标
        private int retrievedChunks;
        private double topScore;

        private String errorMessage;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TraceSummary implements Serializable {
        private static final long serialVersionUID = 1L;

        private String traceId;
        private String workflowName;
        private long totalDurationMs;
        private int totalPromptTokens;
        private int totalCompletionTokens;
        private int grandTotalTokens;
        private BigDecimal totalCostUsd;
        private int totalSpans;
        private List<ExecutionSpan> spans;
    }

    // 内存 Trace 存储缓存 (支持后续持久化至 Postgres/ClickHouse)
    private final Map<String, List<ExecutionSpan>> traceStore = new ConcurrentHashMap<>();
    private final Map<String, ExecutionSpan> activeSpans = new ConcurrentHashMap<>();

    /**
     * 开启一个全局工作流 Trace
     */
    public String startTrace(String workflowName) {
        String traceId = "trc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String spanId = "spn-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        ExecutionSpan rootSpan = ExecutionSpan.builder()
                .traceId(traceId)
                .spanId(spanId)
                .parentSpanId(null)
                .name(workflowName)
                .type(SpanType.WORKFLOW)
                .status(SpanStatus.RUNNING)
                .startTime(System.currentTimeMillis())
                .metadata(new HashMap<>())
                .costUsd(BigDecimal.ZERO)
                .build();

        activeSpans.put(spanId, rootSpan);
        traceStore.computeIfAbsent(traceId, k -> Collections.synchronizedList(new ArrayList<>())).add(rootSpan);
        return traceId;
    }

    /**
     * 创建子 Span (DAG Node, Tool, RAG, LLM)
     */
    public String startSpan(String traceId, String parentSpanId, String spanName, SpanType type) {
        String spanId = "spn-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        ExecutionSpan span = ExecutionSpan.builder()
                .traceId(traceId)
                .spanId(spanId)
                .parentSpanId(parentSpanId)
                .name(spanName)
                .type(type)
                .status(SpanStatus.RUNNING)
                .startTime(System.currentTimeMillis())
                .metadata(new HashMap<>())
                .costUsd(BigDecimal.ZERO)
                .build();

        activeSpans.put(spanId, span);
        List<ExecutionSpan> spans = traceStore.get(traceId);
        if (spans != null) {
            spans.add(span);
        }
        return spanId;
    }

    /**
     * 记录 LLM 调用的 Token 消耗与成本精确核算
     */
    public void recordLlmCall(String spanId, String modelName, int promptTokens, int completionTokens) {
        ExecutionSpan span = activeSpans.get(spanId);
        if (span != null) {
            span.setModelName(modelName);
            span.setPromptTokens(promptTokens);
            span.setCompletionTokens(completionTokens);
            span.setTotalTokens(promptTokens + completionTokens);

            // 模型单价精算 (以标准费率: Input $0.0015/1k, Output $0.002/1k 为基准)
            BigDecimal promptCost = BigDecimal.valueOf(promptTokens).multiply(new BigDecimal("0.0000015"));
            BigDecimal completionCost = BigDecimal.valueOf(completionTokens).multiply(new BigDecimal("0.0000020"));
            span.setCostUsd(promptCost.add(completionCost).setScale(6, RoundingMode.HALF_UP));
        }
    }

    /**
     * 记录 RAG 检索命中度与候选块数
     */
    public void recordRagRetrieval(String spanId, int chunks, double topScore) {
        ExecutionSpan span = activeSpans.get(spanId);
        if (span != null) {
            span.setRetrievedChunks(chunks);
            span.setTopScore(topScore);
        }
    }

    /**
     * 结束一个 Span 的执行
     */
    public void finishSpan(String spanId, boolean success, String errorMessage) {
        ExecutionSpan span = activeSpans.remove(spanId);
        if (span != null) {
            span.setEndTime(System.currentTimeMillis());
            span.setDurationMs(Math.max(1, span.getEndTime() - span.getStartTime()));
            span.setStatus(success ? SpanStatus.SUCCESS : SpanStatus.FAILED);
            span.setErrorMessage(errorMessage);
        }
    }

    /**
     * 获取完整 Trace 执行树与成本瀑布流聚合摘要
     */
    public TraceSummary getTraceSummary(String traceId) {
        List<ExecutionSpan> spans = traceStore.getOrDefault(traceId, Collections.emptyList());
        if (spans.isEmpty()) return null;

        int totalPrompt = 0;
        int totalCompletion = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        long totalDuration = 0;
        String workflowName = "Workflow";

        for (ExecutionSpan span : spans) {
            if (span.getType() == SpanType.WORKFLOW) {
                workflowName = span.getName();
                totalDuration = span.getDurationMs();
            }
            totalPrompt += span.getPromptTokens();
            totalCompletion += span.getCompletionTokens();
            if (span.getCostUsd() != null) {
                totalCost = totalCost.add(span.getCostUsd());
            }
        }

        return TraceSummary.builder()
                .traceId(traceId)
                .workflowName(workflowName)
                .totalDurationMs(totalDuration)
                .totalPromptTokens(totalPrompt)
                .totalCompletionTokens(totalCompletion)
                .grandTotalTokens(totalPrompt + totalCompletion)
                .totalCostUsd(totalCost.setScale(6, RoundingMode.HALF_UP))
                .totalSpans(spans.size())
                .spans(new ArrayList<>(spans))
                .build();
    }
}

package com.agentforge.service.workflow.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.*;

/**
 * 分布式 Agent 执行链路时间线甘特图格式化服务 (Trace Waterfall Gantt Service)
 * 对标 Datadog APM / Jaeger Timeline Gantt 工业级全景时序标准：
 * 1. 自动计算全链路 Root Trace -> Node Span 的绝对时间轴与相对偏移量 (startOffsetMs)；
 * 2. 递归推导 Span 树状深度 (Depth: 0, 1, 2...) 并自适应宽度百分比 (widthPercent)；
 * 3. 赋予不同执行节点 (LLM / RAG / TOOL / DAG) 语义化标签与状态色彩，输出前端可直接渲染的甘特瀑布流模型。
 */
@Service
public class TraceWaterfallGanttService {

    private static final Logger log = LoggerFactory.getLogger(TraceWaterfallGanttService.class);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GanttBarItem implements Serializable {
        private String spanId;
        private String parentSpanId;
        private String name;
        private String spanType;
        private String status;
        private int depth;

        private long startOffsetMs;
        private long durationMs;
        private double offsetPercent;
        private double widthPercent;

        private String badgeColor;
        private String statusColor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GanttTimelineView implements Serializable {
        private String traceId;
        private long totalDurationMs;
        private int spanCount;
        private List<GanttBarItem> items;
    }

    /**
     * 将无序或树状的 ExecutionSpan 列表转化为有序甘特图瀑布流结构
     */
    public GanttTimelineView formatWaterfall(String traceId, List<AgentExecutionTracer.ExecutionSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return GanttTimelineView.builder()
                    .traceId(traceId)
                    .totalDurationMs(0)
                    .spanCount(0)
                    .items(Collections.emptyList())
                    .build();
        }

        // 1. 查找全局基准起始时间 T0 与结束时间 Tend
        long minStart = Long.MAX_VALUE;
        long maxEnd = Long.MIN_VALUE;

        for (AgentExecutionTracer.ExecutionSpan s : spans) {
            if (s.getStartTime() < minStart) minStart = s.getStartTime();
            long end = s.getEndTime() > 0 ? s.getEndTime() : (s.getStartTime() + Math.max(1, s.getDurationMs()));
            if (end > maxEnd) maxEnd = end;
        }

        long totalDuration = Math.max(1, maxEnd - minStart);

        // 2. 构建父子节点关系以计算层级深度 Depth
        Map<String, List<AgentExecutionTracer.ExecutionSpan>> childrenMap = new HashMap<>();
        Set<String> allIds = new HashSet<>();
        for (AgentExecutionTracer.ExecutionSpan s : spans) {
            allIds.add(s.getSpanId());
            String parentId = s.getParentSpanId() != null ? s.getParentSpanId() : "";
            childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(s);
        }

        // 3. 计算每个 Span 的深度 (DFS 或简单查找)
        Map<String, Integer> depthMap = new HashMap<>();
        for (AgentExecutionTracer.ExecutionSpan s : spans) {
            int depth = 0;
            String currParent = s.getParentSpanId();
            while (currParent != null && allIds.contains(currParent) && depth < 20) {
                depth++;
                final String pId = currParent;
                AgentExecutionTracer.ExecutionSpan parentSpan = spans.stream()
                        .filter(x -> Objects.equals(x.getSpanId(), pId))
                        .findFirst().orElse(null);
                currParent = parentSpan != null ? parentSpan.getParentSpanId() : null;
            }
            depthMap.put(s.getSpanId(), depth);
        }

        // 4. 生成有序 GanttBarItem 列表
        List<GanttBarItem> items = new ArrayList<>();
        // 按起始时间排序
        List<AgentExecutionTracer.ExecutionSpan> sortedSpans = new ArrayList<>(spans);
        sortedSpans.sort(Comparator.comparingLong(AgentExecutionTracer.ExecutionSpan::getStartTime));

        for (AgentExecutionTracer.ExecutionSpan s : sortedSpans) {
            long offset = Math.max(0, s.getStartTime() - minStart);
            long dur = s.getDurationMs() > 0 ? s.getDurationMs() : Math.max(1, (s.getEndTime() - s.getStartTime()));

            double offsetPct = Math.min(100.0, (offset * 100.0) / totalDuration);
            double widthPct = Math.max(0.5, Math.min(100.0 - offsetPct, (dur * 100.0) / totalDuration));

            String badgeColor = resolveBadgeColor(s.getType());
            String statusColor = s.getStatus() == AgentExecutionTracer.SpanStatus.FAILED ? "#f56c6c" : "#67c23a";

            GanttBarItem item = GanttBarItem.builder()
                    .spanId(s.getSpanId())
                    .parentSpanId(s.getParentSpanId())
                    .name(s.getName())
                    .spanType(s.getType() != null ? s.getType().name() : "SPAN")
                    .status(s.getStatus() != null ? s.getStatus().name() : "SUCCESS")
                    .depth(depthMap.getOrDefault(s.getSpanId(), 0))
                    .startOffsetMs(offset)
                    .durationMs(dur)
                    .offsetPercent(Math.round(offsetPct * 100.0) / 100.0)
                    .widthPercent(Math.round(widthPct * 100.0) / 100.0)
                    .badgeColor(badgeColor)
                    .statusColor(statusColor)
                    .build();

            items.add(item);
        }

        log.info("📊 [GANTT_FORMATTED] 成功格式化 Trace [{}] 甘特图瀑布流: 总耗时={}ms, Span节点数={}",
                traceId, totalDuration, items.size());

        return GanttTimelineView.builder()
                .traceId(traceId)
                .totalDurationMs(totalDuration)
                .spanCount(items.size())
                .items(items)
                .build();
    }

    private String resolveBadgeColor(AgentExecutionTracer.SpanType type) {
        if (type == null) return "#909399";
        switch (type) {
            case LLM_CALL: return "#8a2be2";      // 紫色 (大模型生成)
            case RAG_RETRIEVAL: return "#409eff";  // 蓝色 (RAG 检索)
            case TOOL_EXECUTION: return "#e6a23c"; // 橙色 (工具/沙箱执行)
            case DAG_NODE: return "#20b2aa";       // 蓝绿色 (工作流节点)
            case WORKFLOW:
            default: return "#909399";             // 灰色 (主流程容器)
        }
    }
}
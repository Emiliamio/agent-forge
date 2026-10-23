package com.agentforge.controller;

import com.agentforge.context.TenantContextHolder;
import com.agentforge.service.agent.llm.arena.ModelArenaTrafficSplitter;
import com.agentforge.service.agent.mcp.McpToolProtocolClient;
import com.agentforge.service.agent.sandbox.SecureCodeSandboxEngine;
import com.agentforge.service.rag.graph.GraphRagEngine;
import com.agentforge.service.rag.quantization.ScalarQuantizationEngine;
import com.agentforge.service.security.api.PromptInjectionGuard;
import com.agentforge.service.security.api.TenantTokenQuotaLimiter;
import com.agentforge.service.workflow.api.AgentExecutionTracer;
import com.agentforge.service.workflow.api.TraceWaterfallGanttService;
import com.agentforge.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 企业级 Apex 核心能力与高阶中枢 API 控制器
 * 集中暴露 GraphRAG 知识图谱、APM 瀑布流甘特图、安全代码沙箱、模型竞技场、MCP 协议与安全护栏
 */
@Slf4j
@Tag(name = "15. 企业级 Apex 核心高阶能力中枢", description = "提供 GraphRAG、APM 甘特图、代码沙箱、模型灰度竞技场、MCP 协议与安全护栏接口")
@RestController
@RequestMapping("/apex")
@RequiredArgsConstructor
public class EnterpriseApexController {

    private final GraphRagEngine graphRagEngine;
    private final TraceWaterfallGanttService traceWaterfallGanttService;
    private final SecureCodeSandboxEngine secureCodeSandboxEngine;
    private final ModelArenaTrafficSplitter modelArenaTrafficSplitter;
    private final McpToolProtocolClient mcpToolProtocolClient;
    private final PromptInjectionGuard promptInjectionGuard;
    private final ScalarQuantizationEngine scalarQuantizationEngine;
    private final TenantTokenQuotaLimiter tenantTokenQuotaLimiter;

    // ── 1. GraphRAG 知识图谱 ──

    @Operation(summary = "从文本抽取实体关系三元组并索引")
    @PostMapping("/graph/extract")
    public Result<List<GraphRagEngine.EntityTriplet>> extractGraphTriplets(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        List<GraphRagEngine.EntityTriplet> triplets = graphRagEngine.extractTriplets(text);
        graphRagEngine.indexTriplets(triplets);
        return Result.success("实体三元组提取与建图成功", triplets);
    }

    @Operation(summary = "执行 GraphRAG 拓扑多跳扩散检索")
    @GetMapping("/graph/search")
    public Result<Set<GraphRagEngine.EntityTriplet>> searchGraph(
            @RequestParam String entity,
            @RequestParam(defaultValue = "2") int maxHops
    ) {
        Set<GraphRagEngine.EntityTriplet> results = graphRagEngine.multiHopSearch(entity, maxHops);
        return Result.success("GraphRAG 扩散搜索成功", results);
    }

    // ── 2. APM 执行链路时间线甘特图 ──

    @Operation(summary = "获取指定 Trace 的 APM 时序瀑布流甘特图")
    @GetMapping("/trace/gantt/{traceId}")
    public Result<TraceWaterfallGanttService.GanttTimelineView> getTraceGantt(@PathVariable String traceId) {
        long now = System.currentTimeMillis();
        List<AgentExecutionTracer.ExecutionSpan> spans = Arrays.asList(
                createSpan("span-root", null, "AgentRootWorkflow", AgentExecutionTracer.SpanType.WORKFLOW, AgentExecutionTracer.SpanStatus.SUCCESS, now, now + 420),
                createSpan("span-sec", "span-root", "PromptInjectionGuard", AgentExecutionTracer.SpanType.DAG_NODE, AgentExecutionTracer.SpanStatus.SUCCESS, now + 5, now + 25),
                createSpan("span-rag", "span-root", "HybridRagRetrieval", AgentExecutionTracer.SpanType.RAG_RETRIEVAL, AgentExecutionTracer.SpanStatus.SUCCESS, now + 30, now + 160),
                createSpan("span-llm", "span-root", "DeepSeek-R1-Inference", AgentExecutionTracer.SpanType.LLM_CALL, AgentExecutionTracer.SpanStatus.SUCCESS, now + 165, now + 390),
                createSpan("span-audit", "span-root", "RagGroundingEvaluator", AgentExecutionTracer.SpanType.TOOL_EXECUTION, AgentExecutionTracer.SpanStatus.SUCCESS, now + 395, now + 418)
        );

        TraceWaterfallGanttService.GanttTimelineView view = traceWaterfallGanttService.formatWaterfall(traceId, spans);
        return Result.success("Trace 甘特瀑布流解析成功", view);
    }

    private AgentExecutionTracer.ExecutionSpan createSpan(
            String id, String parentId, String name,
            AgentExecutionTracer.SpanType type,
            AgentExecutionTracer.SpanStatus status,
            long start, long end
    ) {
        AgentExecutionTracer.ExecutionSpan span = new AgentExecutionTracer.ExecutionSpan();
        span.setSpanId(id);
        span.setParentSpanId(parentId);
        span.setName(name);
        span.setType(type);
        span.setStatus(status);
        span.setStartTime(start);
        span.setEndTime(end);
        span.setDurationMs(end - start);
        return span;
    }

    // ── 3. 安全受限代码沙箱 ──

    @Operation(summary = "安全受限代码沙箱运行")
    @PostMapping("/sandbox/run")
    public Result<SecureCodeSandboxEngine.SandboxResult> runSandbox(@RequestBody Map<String, Object> body) {
        String code = (String) body.getOrDefault("code", "");
        long timeoutMs = body.containsKey("timeoutMs") ? Long.parseLong(body.get("timeoutMs").toString()) : 3000L;
        SecureCodeSandboxEngine.SandboxResult result = secureCodeSandboxEngine.execute(code, timeoutMs);
        return Result.success("沙箱执行评估完成", result);
    }

    // ── 4. 多模型竞技场与灰度路由 ──

    @Operation(summary = "配置多模型候选池")
    @PostMapping("/arena/candidates")
    public Result<String> configureArenaCandidates(@RequestBody List<ModelArenaTrafficSplitter.ModelCandidate> candidates) {
        modelArenaTrafficSplitter.setCandidates(candidates);
        return Result.success("模型竞技场候选池配置已更新", "OK");
    }

    @Operation(summary = "获取用户会话一致性模型路由决策")
    @GetMapping("/arena/route")
    public Result<Map<String, String>> routeModel(@RequestParam String sessionOrUserId) {
        String selectedModel = modelArenaTrafficSplitter.routeConsistent(sessionOrUserId);
        Map<String, String> res = new HashMap<>();
        res.put("sessionOrUserId", sessionOrUserId);
        res.put("routedModel", selectedModel);
        return Result.success("路由决策成功", res);
    }

    @Operation(summary = "提交模型竞技场满意度评价")
    @PostMapping("/arena/feedback")
    public Result<String> recordModelFeedback(@RequestBody ArenaFeedbackRequest request) {
        modelArenaTrafficSplitter.recordUserFeedback(request.getModelId(), request.isPositive());
        return Result.success("模型评价反馈已记录", "OK");
    }

    // ── 5. Anthropic MCP 协议原生工具生态 ──

    @Operation(summary = "获取已发现的 MCP 工具列表")
    @GetMapping("/mcp/tools")
    public Result<List<McpToolProtocolClient.McpToolDefinition>> listMcpTools() {
        return Result.success(mcpToolProtocolClient.listTools());
    }

    @Operation(summary = "远程调度 MCP 工具执行")
    @PostMapping("/mcp/call")
    public Result<McpToolProtocolClient.McpCallResult> callMcpTool(@RequestBody McpCallRequest request) {
        McpToolProtocolClient.McpCallResult result = mcpToolProtocolClient.executeToolCall(request.getToolName(), request.getArguments());
        return Result.success("MCP 工具调度完成", result);
    }

    // ── 6. 安全护栏与配额限制 ──

    @Operation(summary = "Prompt 注入与越狱对抗实时审计")
    @PostMapping("/guard/inspect")
    public Result<PromptInjectionGuard.GuardResult> inspectPrompt(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        PromptInjectionGuard.GuardResult result = promptInjectionGuard.inspect(text);
        return Result.success("安全审计完成", result);
    }

    @Operation(summary = "租户单日 Token 预算与并发限流检查")
    @PostMapping("/quota/check")
    public Result<TenantTokenQuotaLimiter.QuotaDecision> checkQuota(@RequestBody QuotaCheckRequest request) {
        Long tenantId = request.getTenantId() != null ? request.getTenantId() : TenantContextHolder.getTenantId();
        TenantTokenQuotaLimiter.QuotaDecision decision = tenantTokenQuotaLimiter.checkAndAcquire(tenantId, request.getEstimatedTokens());
        return Result.success("配额决策已生成", decision);
    }

    // ── 7. SQ8 标量量化压缩体验 ──

    @Operation(summary = "SQ8 向量标量量化压缩与相似度保真度评测")
    @PostMapping("/quantization/compress")
    public Result<Map<String, Object>> testQuantization(@RequestBody Map<String, Object> body) {
        float[] vector = new float[1536];
        Arrays.fill(vector, 0.05f);
        vector[0] = 0.88f;
        vector[10] = 0.55f;

        ScalarQuantizationEngine.QuantizedVector qv = scalarQuantizationEngine.quantize(vector);
        float[] restored = scalarQuantizationEngine.dequantize(qv);
        double similarity = scalarQuantizationEngine.exactCosineSimilarity(vector, restored);

        Map<String, Object> resp = new HashMap<>();
        resp.put("originalBytes", vector.length * 4);
        resp.put("compressedBytes", qv.getData().length);
        resp.put("compressionRatio", "75.0%");
        resp.put("fidelityCosineSimilarity", similarity);

        return Result.success("量化压缩评估完成", resp);
    }

    // ── DTOs ──

    @Data
    public static class ArenaFeedbackRequest {
        private String modelId;
        private boolean positive;
    }

    @Data
    public static class McpCallRequest {
        private String toolName;
        private Map<String, Object> arguments;
    }

    @Data
    public static class QuotaCheckRequest {
        private Long tenantId;
        private int estimatedTokens = 100;
    }
}

package com.agentforge;

import com.agentforge.service.agent.mcp.McpToolProtocolClient;
import com.agentforge.service.agent.stream.StructuredSseStreamDispatcher;
import com.agentforge.service.rag.eval.RagGroundingEvaluator;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@DisplayName("AgentForge 行业天花板套件深度测试：RAG 事实性护栏 + MCP 原生客户端 + DeepSeek-R1 结构化流分发")
public class EnterpriseUltimateCeilingTest {

    @Test
    @DisplayName("测试 RAG 事实性与幻觉评估护栏 (RagGroundingEvaluator)")
    void testRagGroundingEvaluator() {
        RagGroundingEvaluator evaluator = new RagGroundingEvaluator();

        List<String> contextChunks = List.of(
                "公司 2024 年第四季度净利润为 5.8 亿元，同比增长 18.5%。",
                "研发支出占总营业收入的 12.3%，核心投向为虚拟线程与高并发架构。"
        );

        // 1. 真实引文忠实生成的回答 -> 应当评定为 Grounded (事实可信)
        String faithfulAnswer = "根据财报，公司 2024 年第四季度净利润达到 5.8 亿元，同比增长 18.5%，研发支出占比为 12.3%。";
        RagGroundingEvaluator.GroundingResult faithfulResult = evaluator.evaluate(faithfulAnswer, contextChunks);
        Assertions.assertTrue(faithfulResult.isGrounded());
        Assertions.assertTrue(faithfulResult.getGroundingScore() >= 0.65);
        Assertions.assertNull(faithfulResult.getDisclaimer());

        // 2. 凭空捏造事实的回答 (幻觉) -> 应当判定为 Ungrounded 并输出风险免责提示
        String hallucinatedAnswer = "公司预计将在火星建立第三个数据中心，并全员配发量子计算机进行办公。";
        RagGroundingEvaluator.GroundingResult hallucinatedResult = evaluator.evaluate(hallucinatedAnswer, contextChunks);
        Assertions.assertFalse(hallucinatedResult.isGrounded());
        Assertions.assertTrue(hallucinatedResult.getGroundingScore() < 0.30);
        Assertions.assertNotNull(hallucinatedResult.getDisclaimer());
        Assertions.assertTrue(hallucinatedResult.getDisclaimer().contains("事实性存疑"));
    }

    @Test
    @DisplayName("测试 Anthropic MCP (Model Context Protocol) 原生客户端 (McpToolProtocolClient)")
    void testMcpProtocolClient() {
        McpToolProtocolClient client = new McpToolProtocolClient();

        // 1. 注册外部标准 MCP 工具
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("properties", Map.of("query", Map.of("type", "string")));
        McpToolProtocolClient.McpToolDefinition tool = new McpToolProtocolClient.McpToolDefinition(
                "github_issue_search",
                "Search GitHub issues by keywords",
                schema
        );
        client.registerTool(tool);

        // 2. tools/list 校验
        List<McpToolProtocolClient.McpToolDefinition> tools = client.listTools();
        Assertions.assertEquals(1, tools.size());
        Assertions.assertEquals("github_issue_search", tools.get(0).getName());

        // 3. 构建 JSON-RPC 2.0 报文
        JSONObject rpcPayload = client.buildJsonRpcCallPayload("github_issue_search", Map.of("query", "OOM fix"));
        Assertions.assertEquals("2.0", rpcPayload.getString("jsonrpc"));
        Assertions.assertEquals("tools/call", rpcPayload.getString("method"));

        // 4. tools/call 执行并解析
        McpToolProtocolClient.McpCallResult callResult = client.executeToolCall("github_issue_search", Map.of("query", "OOM fix"));
        Assertions.assertTrue(callResult.isSuccess());
        Assertions.assertFalse(callResult.isError());
        Assertions.assertTrue(callResult.getContent().contains("SUCCESS"));
    }

    @Test
    @DisplayName("测试 DeepSeek-R1 深度思考流与结构化 SSE 事件帧分发 (StructuredSseStreamDispatcher)")
    void testStructuredSseStreamDispatcher() {
        StructuredSseStreamDispatcher dispatcher = new StructuredSseStreamDispatcher();
        StructuredSseStreamDispatcher.StreamParseState state = new StructuredSseStreamDispatcher.StreamParseState();

        // 1. 发送带有 <think> 标签的流分块
        String chunk1 = "Hello! <think>Let me analyze the problem step by step.\nFirst check DB status.";
        List<StructuredSseStreamDispatcher.SseFrame> frames1 = dispatcher.processChunk(chunk1, state);
        Assertions.assertTrue(state.isInThinking());
        Assertions.assertTrue(frames1.stream().anyMatch(f -> f.getEventType() == StructuredSseStreamDispatcher.EventType.THOUGHT_START));

        // 2. 发送思考闭合 </think> 并接正常答案
        String chunk2 = "\nDone thinking.</think> The database is fully operational.";
        List<StructuredSseStreamDispatcher.SseFrame> frames2 = dispatcher.processChunk(chunk2, state);
        Assertions.assertFalse(state.isInThinking());
        Assertions.assertTrue(frames2.stream().anyMatch(f -> f.getEventType() == StructuredSseStreamDispatcher.EventType.THOUGHT_END));
        Assertions.assertTrue(frames2.stream().anyMatch(f -> f.getEventType() == StructuredSseStreamDispatcher.EventType.ANSWER_DELTA));

        // 3. 验证 SSE 协议文本规范
        StructuredSseStreamDispatcher.SseFrame doneFrame = dispatcher.createDoneFrame(100, 50);
        String sseText = doneFrame.toSseFormat();
        Assertions.assertTrue(sseText.startsWith("event: done\ndata:"));
        Assertions.assertTrue(sseText.endsWith("\n\n"));
    }
}
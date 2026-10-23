package com.agentforge.service;

import com.agentforge.entity.AgentApp;
import com.agentforge.entity.WorkflowExecution;
import com.agentforge.exception.BusinessException;
import com.agentforge.mapper.AgentAppMapper;
import com.agentforge.mapper.WorkflowExecutionMapper;
import com.agentforge.service.agent.ReActAgentService;
import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.agent.llm.LlmRequest;
import com.agentforge.service.agent.llm.LlmResponse;
import com.agentforge.service.agent.llm.ResilientLlmFailoverService;
import com.agentforge.service.rag.RagService;
import com.agentforge.service.workflow.engine.WorkflowCheckpointManager;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import com.agentforge.service.workflow.node.*;
import com.agentforge.tools.ToolRegistry;
import com.agentforge.vo.Citation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgentAndWorkflowExecutionTest: Agent ReAct 编排、LLM 容灾与工作流执行单测")
public class AgentAndWorkflowExecutionTest {

    @Mock private LlmClient llmClient;
    @Mock private ToolRegistry toolRegistry;
    @Mock private RagService ragService;
    @Mock private AgentAppMapper agentAppMapper;
    @Mock private WorkflowExecutionMapper workflowExecutionMapper;
    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("ReActAgentService: 完整循环与状态流转")
    void testReActAgentService() {
        ReActAgentService agentService = new ReActAgentService(llmClient, toolRegistry, ragService, agentAppMapper);

        // 1. Agent 不存在或被禁用
        when(agentAppMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> agentService.runAgent(999L, "问句", 3));

        AgentApp disabledApp = new AgentApp();
        disabledApp.setId(2L);
        disabledApp.setStatus(0);
        when(agentAppMapper.selectById(2L)).thenReturn(disabledApp);
        assertThrows(BusinessException.class, () -> agentService.runAgent(2L, "问句", 3));

        // 2. 正常直接回答
        AgentApp app = new AgentApp();
        app.setId(1L);
        app.setName("报销问答助手");
        app.setStatus(1);
        app.setDatasetIds("[1]");
        app.setToolsConfig("[\"calculator\"]");
        app.setSystemPrompt("You are helpful");
        when(agentAppMapper.selectById(1L)).thenReturn(app);

        when(toolRegistry.generateToolsPrompt(anyList())).thenReturn("Tool: calculator");
        when(ragService.retrieveCitations(anyList(), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(Citation.builder().documentName("制度.pdf").snippet("标准500元").build()));

        LlmResponse answerResp = LlmResponse.builder()
                .content("Thought: 直接回答\nFinal Answer: 北京住宿标准是 500 元。")
                .totalTokens(80)
                .build();
        when(llmClient.generate(any(LlmRequest.class))).thenReturn(answerResp);

        com.agentforge.vo.AgentExecutionResult result = agentService.runAgent(1L, "北京住宿标准", 5);
        assertNotNull(result);
        assertEquals("北京住宿标准是 500 元。", result.getFinalAnswer());
        assertFalse(result.getCitations().isEmpty());

        // 3. chatStream 流式
        when(llmClient.generateStream(any())).thenReturn(Flux.just("北京", "住宿", "500元"));
        Flux<String> stream = agentService.chatStream(1L, "北京标准");
        List<String> collected = stream.collectList().block();
        assertNotNull(collected);
        assertFalse(collected.isEmpty());

        // 4. ReActStep 与 AgentExecutionResult 实体结构
        com.agentforge.vo.ReActStep step = com.agentforge.vo.ReActStep.builder()
                .stepIndex(1)
                .thought("思考中")
                .action("calculator")
                .actionInput(Map.of("expr", "1+1"))
                .observation("2")
                .durationMs(15)
                .build();
        assertEquals(1, step.getStepIndex());
        assertEquals("calculator", step.getAction());

        // 5. 多轮 Action 执行分支
        when(agentAppMapper.selectById(10L)).thenReturn(app);
        when(toolRegistry.executeTool(eq("calculator"), anyMap())).thenReturn("42");
        LlmResponse actionResp = LlmResponse.builder()
                .content("Thought: 需要计算\nAction: calculator\nAction Input: {\"expr\":\"21*2\"}\nObservation: 42")
                .totalTokens(40)
                .build();
        LlmResponse finalResp = LlmResponse.builder()
                .content("Thought: 得到结果\nFinal Answer: 计算结果为 42。")
                .totalTokens(30)
                .build();
        when(llmClient.generate(any(LlmRequest.class)))
                .thenReturn(actionResp)
                .thenReturn(finalResp);

        com.agentforge.vo.AgentExecutionResult multiStepRes = agentService.runAgent(10L, "计算21乘2", 3);
        assertNotNull(multiStepRes);
        assertEquals("计算结果为 42。", multiStepRes.getFinalAnswer());
    }

    @Test
    @DisplayName("ResilientLlmFailoverService: 级联降级与熔断防护")
    void testResilientLlmFailover() {
        ResilientLlmFailoverService failoverService = new ResilientLlmFailoverService(llmClient);

        // 1. 主模型直接成功
        LlmRequest req1 = LlmRequest.builder()
                .systemPrompt("sys")
                .userPrompt("user1")
                .provider("deepseek")
                .modelName("deepseek-chat")
                .temperature(0.5)
                .build();
        LlmResponse successResp = LlmResponse.builder().content("成功响应").totalTokens(50).build();
        when(llmClient.generate(argThat(r -> r != null && "user1".equals(r.getUserPrompt())))).thenReturn(successResp);

        LlmResponse res1 = failoverService.generateWithFailover(req1);
        assertNotNull(res1);
        assertEquals("成功响应", res1.getContent());

        // 2. 主模型失败后降级到兜底回复
        LlmRequest req2 = LlmRequest.builder()
                .systemPrompt("sys")
                .userPrompt("user_fail")
                .provider("deepseek")
                .modelName("deepseek-chat")
                .build();
        when(llmClient.generate(argThat(r -> r != null && "user_fail".equals(r.getUserPrompt()))))
                .thenThrow(new RuntimeException("API 超时或熔断"));

        LlmResponse res2 = failoverService.generateWithFailover(req2);
        assertNotNull(res2);
        assertTrue(res2.getContent().contains("user_fail"));
    }

    @Test
    @DisplayName("WorkflowCheckpointManager: 保存与 Saga 补偿")
    void testWorkflowCheckpointManager() {
        WorkflowCheckpointManager manager = new WorkflowCheckpointManager(workflowExecutionMapper);

        WorkflowExecution mockExec = new WorkflowExecution();
        mockExec.setId(100L);
        when(workflowExecutionMapper.selectById(100L)).thenReturn(mockExec);

        WorkflowContext ctx = WorkflowContext.builder()
                .variables(new HashMap<>(Map.of("res", 1)))
                .stepLogs(new ArrayList<>())
                .build();

        // 1. 保存检查点
        manager.saveCheckpoint(100L, "node_1", Map.of("out", "ok"), ctx);
        verify(workflowExecutionMapper, atLeastOnce()).updateById(any());

        // 空执行 ID 幂等
        manager.saveCheckpoint(null, "node_1", Map.of(), ctx);

        // 2. 触发 Saga 逆向补偿
        Stack<DagModel.DagNode> stack = new Stack<>();
        DagModel.DagNode n1 = DagModel.DagNode.builder().id("n1").name("HTTP 扣款").data(Map.of("rollbackUrl", "http://rollback.internal")).build();
        DagModel.DagNode n2 = DagModel.DagNode.builder().id("n2").name("本地日志").build();
        stack.push(n1);
        stack.push(n2);

        manager.triggerSagaCompensation(stack, ctx);
        assertTrue(stack.isEmpty());
    }

    @Test
    @DisplayName("Workflow Nodes: LlmNodeExecutor & KnowledgeRetrievalNodeExecutor")
    void testLlmAndKnowledgeNodes() {
        WorkflowContext ctx = WorkflowContext.builder()
                .variables(new HashMap<>(Map.of("query", "北京出差报销", "userId", 1001)))
                .build();

        // 1. KnowledgeRetrievalNodeExecutor
        KnowledgeRetrievalNodeExecutor knowNode = new KnowledgeRetrievalNodeExecutor(ragService);
        assertEquals("KNOWLEDGE", knowNode.getNodeType());

        when(ragService.retrieveCitations(anyList(), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(Citation.builder().documentName("政策.pdf").snippet("上限500").score(0.88).build()));

        DagModel.DagNode knowDef = DagModel.DagNode.builder()
                .id("know_1")
                .type("KNOWLEDGE")
                .data(Map.of("query", "{{query}}", "topK", 3, "datasetIds", List.of(1L, 2L)))
                .build();

        NodeExecutionResult knowRes = knowNode.execute(ctx, knowDef).block();
        assertNotNull(knowRes);
        assertEquals("SUCCESS", knowRes.getStatus());
        assertNotNull(ctx.getVariable("know_1.context"));

        // 2. LlmNodeExecutor
        LlmNodeExecutor llmNode = new LlmNodeExecutor(llmClient);
        assertEquals("LLM", llmNode.getNodeType());

        when(llmClient.generate(any())).thenReturn(LlmResponse.builder().content("LLM 回复文本").totalTokens(120).modelName("deepseek-chat").build());

        DagModel.DagNode llmDef = DagModel.DagNode.builder()
                .id("llm_1")
                .type("LLM")
                .data(Map.of("systemPrompt", "系统提示", "userPrompt", "用户: {{query}}"))
                .build();

        NodeExecutionResult llmRes = llmNode.execute(ctx, llmDef).block();
        assertNotNull(llmRes);
        assertEquals("SUCCESS", llmRes.getStatus());
        assertEquals("LLM 回复文本", ctx.getVariable("llm_1.text"));
    }

    @Test
    @DisplayName("Workflow Nodes: Text2SqlNodeExecutor & HttpRequestNodeExecutor")
    void testText2SqlAndHttpNodes() throws Exception {
        WorkflowContext ctx = WorkflowContext.builder()
                .variables(new HashMap<>(Map.of("query", "查华北地区销售额")))
                .build();

        // 1. Text2SqlNodeExecutor
        Text2SqlNodeExecutor sqlNode = new Text2SqlNodeExecutor(llmClient, jdbcTemplate);
        assertEquals("TEXT2SQL", sqlNode.getNodeType());

        when(llmClient.generate(any())).thenReturn(LlmResponse.builder()
                .content("```sql\nSELECT region, sales_amount FROM sales_record WHERE region = 'North';\n```")
                .build());
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("region", "North", "sales_amount", 50000)));

        DagModel.DagNode sqlDef = DagModel.DagNode.builder()
                .id("sql_1")
                .type("TEXT2SQL")
                .data(Map.of("query", "{{query}}"))
                .build();

        NodeExecutionResult sqlRes = sqlNode.execute(ctx, sqlDef).block();
        assertNotNull(sqlRes);
        assertEquals("SUCCESS", sqlRes.getStatus());

        // Text2Sql 安全拦截验证
        assertThrows(SecurityException.class, () -> sqlNode.validateAndHardenSql("SELECT * FROM sys_user"));
        assertThrows(SecurityException.class, () -> sqlNode.validateAndHardenSql("SELECT pg_sleep(5)"));
        assertThrows(SecurityException.class, () -> sqlNode.validateAndHardenSql("DROP TABLE sales_record"));

        // 2. HttpRequestNodeExecutor
        HttpRequestNodeExecutor httpNode = new HttpRequestNodeExecutor();
        assertEquals("HTTP", httpNode.getNodeType());

        DagModel.DagNode httpDef = DagModel.DagNode.builder()
                .id("http_1")
                .type("HTTP")
                .data(Map.of("url", "http://127.0.0.1:9999/not-exist", "method", "GET"))
                .build();

        NodeExecutionResult httpRes = httpNode.execute(ctx, httpDef).block();
        assertNotNull(httpRes); // 连接失败优雅捕获为 fail 结果
        assertEquals("FAILED", httpRes.getStatus());
    }

    @Test
    @DisplayName("LlmClient: 模拟引擎、LangChain 真实调用与流式切片")
    void testLlmClientDirectly() {
        LlmClient client = new LlmClient();
        client.init();

        // 1. 无线上 key 时走模拟引擎
        LlmRequest req1 = LlmRequest.builder()
                .systemPrompt("系统提示词")
                .userPrompt("测试模拟输出")
                .history(List.of(
                        new LlmRequest.ChatMessage("user", "上一句"),
                        new LlmRequest.ChatMessage("assistant", "上一句回答")
                ))
                .build();
        LlmResponse resp1 = client.generate(req1);
        assertNotNull(resp1);
        assertTrue(resp1.isFallback());
        assertEquals("agentforge-mock-engine", resp1.getModelName());

        // 2. 包含 [来源 # 的提示词分支
        LlmRequest reqWithCite = LlmRequest.builder()
                .systemPrompt("参考资料: [来源 #1] 制度")
                .userPrompt("问句")
                .build();
        LlmResponse respCite = client.generate(reqWithCite);
        assertNotNull(respCite);

        // 3. 线上模型 mock 成功与异常路径
        dev.langchain4j.model.chat.ChatLanguageModel mockDs = mock(dev.langchain4j.model.chat.ChatLanguageModel.class);
        dev.langchain4j.data.message.AiMessage aiMsg = dev.langchain4j.data.message.AiMessage.from("DeepSeek 回复");
        dev.langchain4j.model.output.Response<dev.langchain4j.data.message.AiMessage> dsResp =
                dev.langchain4j.model.output.Response.from(aiMsg);
        when(mockDs.generate(anyList())).thenReturn(dsResp);
        org.springframework.test.util.ReflectionTestUtils.setField(client, "deepseekModel", mockDs);

        LlmResponse respDs = client.generate(req1);
        assertEquals("DeepSeek 回复", respDs.getContent());
        assertFalse(respDs.isFallback());

        // 4. 流式切片
        Flux<String> stream = client.generateStream(req1);
        List<String> streamTokens = stream.collectList().block();
        assertNotNull(streamTokens);
        assertFalse(streamTokens.isEmpty());
    }
}
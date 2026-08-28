package com.agentforge;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.workflow.engine.DagGraph;
import com.agentforge.service.workflow.engine.WorkflowEngine;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import com.agentforge.service.workflow.node.CodeNodeExecutor;
import com.agentforge.service.workflow.node.ComplianceReviewNodeExecutor;
import com.agentforge.service.workflow.node.ConditionNodeExecutor;
import com.agentforge.service.workflow.node.EndNodeExecutor;
import com.agentforge.service.workflow.node.HttpRequestNodeExecutor;
import com.agentforge.service.workflow.node.KnowledgeRetrievalNodeExecutor;
import com.agentforge.service.workflow.node.LlmNodeExecutor;
import com.agentforge.service.workflow.node.NodeExecutorFactory;
import com.agentforge.service.workflow.node.StartNodeExecutor;
import com.agentforge.tools.ToolRegistry;
import com.agentforge.tools.builtin.CalculatorTool;
import com.agentforge.tools.builtin.CurrentTimeTool;
import com.agentforge.tools.openapi.OpenApiTool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@DisplayName("Phase 3 & 4: 响应式 DAG 工作流引擎、ReAct 智能体与合规工作流测试")
public class DagWorkflowEngineTest {

    private WorkflowEngine workflowEngine;
    private ToolRegistry toolRegistry;
    private LlmClient llmClient;

    @BeforeEach
    void setUp() {
        this.llmClient = new LlmClient();
        StartNodeExecutor startNode = new StartNodeExecutor();
        LlmNodeExecutor llmNode = new LlmNodeExecutor(llmClient);
        ConditionNodeExecutor conditionNode = new ConditionNodeExecutor();
        HttpRequestNodeExecutor httpNode = new HttpRequestNodeExecutor();
        CodeNodeExecutor codeNode = new CodeNodeExecutor();
        EndNodeExecutor endNode = new EndNodeExecutor();
        ComplianceReviewNodeExecutor complianceNode = new ComplianceReviewNodeExecutor(llmClient);

        NodeExecutorFactory factory = new NodeExecutorFactory(List.of(
                startNode, llmNode, conditionNode, httpNode, codeNode, endNode, complianceNode
        ));
        this.workflowEngine = new WorkflowEngine(factory);

        CalculatorTool calc = new CalculatorTool();
        CurrentTimeTool time = new CurrentTimeTool();
        this.toolRegistry = new ToolRegistry(List.of(calc, time));
    }

    @Test
    @DisplayName("测试 Kahn 算法拓扑分层与无依赖节点并行分组")
    void testKahnTopologicalTiers() {
        DagModel.DagNode nStart = DagModel.DagNode.builder().id("start").type("START").name("开始").build();
        DagModel.DagNode nLlm1 = DagModel.DagNode.builder().id("llm_1").type("LLM").name("模型1").build();
        DagModel.DagNode nLlm2 = DagModel.DagNode.builder().id("llm_2").type("LLM").name("模型2").build();
        DagModel.DagNode nEnd = DagModel.DagNode.builder().id("end").type("END").name("结束").build();

        List<DagModel.DagEdge> edges = List.of(
                DagModel.DagEdge.builder().id("e1").source("start").target("llm_1").build(),
                DagModel.DagEdge.builder().id("e2").source("start").target("llm_2").build(),
                DagModel.DagEdge.builder().id("e3").source("llm_1").target("end").build(),
                DagModel.DagEdge.builder().id("e4").source("llm_2").target("end").build()
        );

        DagModel model = DagModel.builder()
                .nodes(List.of(nStart, nLlm1, nLlm2, nEnd))
                .edges(edges)
                .build();

        DagGraph graph = new DagGraph(model);
        List<List<DagModel.DagNode>> tiers = graph.computeTopologicalTiers();

        Assertions.assertEquals(3, tiers.size());
        Assertions.assertEquals(1, tiers.get(0).size());
        Assertions.assertEquals("start", tiers.get(0).get(0).getId());
        Assertions.assertEquals(2, tiers.get(1).size());
        Assertions.assertEquals(1, tiers.get(2).size());
        Assertions.assertEquals("end", tiers.get(2).get(0).getId());
    }

    @Test
    @DisplayName("测试 DAG 环路检测与死锁阻断 (Tarjan/Kahn Cycle Detection)")
    void testCycleDetection() {
        DagModel.DagNode nStart = DagModel.DagNode.builder().id("start").type("START").name("开始").build();
        DagModel.DagNode nA = DagModel.DagNode.builder().id("node_a").type("CODE").name("节点A").build();
        DagModel.DagNode nB = DagModel.DagNode.builder().id("node_b").type("CODE").name("节点B").build();

        List<DagModel.DagEdge> edges = List.of(
                DagModel.DagEdge.builder().id("e1").source("start").target("node_a").build(),
                DagModel.DagEdge.builder().id("e2").source("node_a").target("node_b").build(),
                DagModel.DagEdge.builder().id("e3").source("node_b").target("node_a").build()
        );

        DagModel model = DagModel.builder()
                .nodes(List.of(nStart, nA, nB))
                .edges(edges)
                .build();

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> {
            new DagGraph(model).validate();
        });

        Assertions.assertEquals(ErrorCode.WORKFLOW_CYCLE_DETECTED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("测试合同与合规智能审查工作流节点调度")
    void testComplianceReviewWorkflow() {
        DagModel.DagNode nStart = DagModel.DagNode.builder().id("start").type("START").name("开始").build();
        DagModel.DagNode nCompliance = DagModel.DagNode.builder()
                .id("comp_1")
                .type("COMPLIANCE")
                .name("合规自检")
                .data(Map.of(
                        "standardClause", "招标文件要求响应时间不得超过 30 分钟，违约金为合同额的 5%。",
                        "targetDocument", "应标书响应时间承诺为 60 分钟。"
                ))
                .build();
        DagModel.DagNode nEnd = DagModel.DagNode.builder()
                .id("end")
                .type("END")
                .name("结束")
                .data(Map.of("outputMapping", Map.of("report", "{{comp_1.report}}")))
                .build();

        DagModel model = DagModel.builder()
                .nodes(List.of(nStart, nCompliance, nEnd))
                .edges(List.of(
                        DagModel.DagEdge.builder().id("e1").source("start").target("comp_1").build(),
                        DagModel.DagEdge.builder().id("e2").source("comp_1").target("end").build()
                ))
                .build();

        WorkflowContext context = WorkflowContext.builder()
                .tenantId(1L)
                .workflowId(202L)
                .inputs(Map.of())
                .build();

        WorkflowContext result = workflowEngine.executeWorkflow(model, context).block();
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getOutputs().get("report"));
        Assertions.assertEquals(3, result.getStepLogs().size());
    }

    @Test
    @DisplayName("测试 JSONPath 深层嵌套变量解析与端到端工作流调度")
    void testWorkflowExecutionWithJsonPath() {
        DagModel.DagNode nStart = DagModel.DagNode.builder().id("start").type("START").name("开始").build();
        DagModel.DagNode nCode = DagModel.DagNode.builder()
                .id("code_1")
                .type("CODE")
                .name("数据提取")
                .data(Map.of("outputKey", "greeting", "template", "你好，{{user.profile.name}}！你的角色是: {{user.role}}"))
                .build();
        DagModel.DagNode nEnd = DagModel.DagNode.builder()
                .id("end")
                .type("END")
                .name("结束")
                .data(Map.of("outputMapping", Map.of("final_text", "{{greeting}}", "code", "200")))
                .build();

        DagModel model = DagModel.builder()
                .nodes(List.of(nStart, nCode, nEnd))
                .edges(List.of(
                        DagModel.DagEdge.builder().id("e1").source("start").target("code_1").build(),
                        DagModel.DagEdge.builder().id("e2").source("code_1").target("end").build()
                ))
                .build();

        Map<String, Object> nestedUser = Map.of(
                "user", Map.of(
                        "profile", Map.of("name", "首席架构师"),
                        "role", "OWNER"
                )
        );

        WorkflowContext context = WorkflowContext.builder()
                .tenantId(1L)
                .workflowId(101L)
                .inputs(nestedUser)
                .build();

        WorkflowContext result = workflowEngine.executeWorkflow(model, context).block();

        Assertions.assertNotNull(result);
        Assertions.assertEquals("你好，首席架构师！你的角色是: OWNER", result.getOutputs().get("final_text"));
        Assertions.assertEquals("200", result.getOutputs().get("code"));
        Assertions.assertEquals(3, result.getStepLogs().size());
    }

    @Test
    @DisplayName("测试动态 OpenAPI 工具与内置工具调度")
    void testOpenApiAndBuiltInTools() {
        String calcResult = toolRegistry.executeTool("calculator", Map.of("expression", "100 * 25 + 50"));
        Assertions.assertTrue(calcResult.contains("2550"));

        String timeResult = toolRegistry.executeTool("current_time", Map.of());
        Assertions.assertTrue(timeResult.contains("当前精确系统时间"));

        OpenApiTool openApiTool = OpenApiTool.builder()
                .name("user_service_api")
                .description("查询用户详细信息")
                .targetUrl("https://httpbin.org/get")
                .httpMethod("GET")
                .build();

        Assertions.assertEquals("user_service_api", openApiTool.getName());
        Assertions.assertNotNull(openApiTool.getDescription());
    }
}

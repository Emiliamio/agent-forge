package com.agentforge.service.workflow.node;

import cn.hutool.core.map.MapUtil;
import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.agent.llm.LlmRequest;
import com.agentforge.service.agent.llm.LlmResponse;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * COMPLIANCE 合同与招投标合规智能审查节点执行器
 * 自动比对应标条款与招标文件，识别废标项与高危违约风险，生成结构化审计报告
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceReviewNodeExecutor implements WorkflowNodeExecutor {

    private final LlmClient llmClient;

    @Override
    public String getNodeType() {
        return "COMPLIANCE";
    }

    @Override
    public Mono<NodeExecutionResult> execute(WorkflowContext context, DagModel.DagNode node) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();

            Map<String, Object> nodeData = node.getData() != null ? node.getData() : new HashMap<>();
            String standardClause = MapUtil.getStr(nodeData, "standardClause", "{{rag_context}}");
            String targetDoc = MapUtil.getStr(nodeData, "targetDocument", "{{query}}");

            String resolvedStandard = context.resolveTemplate(standardClause);
            String resolvedTarget = context.resolveTemplate(targetDoc);

            String systemPrompt = """
                    你是一个顶尖企业法务与招投标合规审计总监。
                    请对标【合规基准条款】，对【目标合同/应标文件】进行严格合规性审查。
                    
                    输出要求：
                    1. 【合规等级】：高风险 (RED) / 中风险 (YELLOW) / 合规 (GREEN)
                    2. 【废标与重大违约风险点清单】：逐条列出差异与风险解释。
                    3. 【法务修改建议】：给出明确的修改措辞。
                    """;

            String userPrompt = String.format("""
                    【合规基准标准】：
                    %s
                    
                    【待审查文档内容】：
                    %s
                    """, resolvedStandard, resolvedTarget);

            LlmResponse response = llmClient.generate(LlmRequest.builder()
                    .systemPrompt(systemPrompt)
                    .userPrompt(userPrompt)
                    .temperature(0.2)
                    .build());

            String report = response.getContent();

            context.setVariable(node.getId() + ".report", report);
            context.setVariable("compliance_report", report);

            Map<String, Object> outputs = new HashMap<>();
            outputs.put("complianceReport", report);
            outputs.put("riskLevel", report.contains("RED") ? "HIGH_RISK" : "SAFE");

            long duration = System.currentTimeMillis() - startTime;
            return NodeExecutionResult.success(node.getId(), getNodeType(), outputs, duration);
        });
    }
}

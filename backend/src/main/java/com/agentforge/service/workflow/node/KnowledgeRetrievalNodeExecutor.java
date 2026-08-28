package com.agentforge.service.workflow.node;

import cn.hutool.core.map.MapUtil;
import com.agentforge.service.rag.RagService;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import com.agentforge.vo.Citation;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KNOWLEDGE 知识检索节点执行器 (无缝对接 Phase 2 混合检索 RAG)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRetrievalNodeExecutor implements WorkflowNodeExecutor {

    private final RagService ragService;

    @Override
    public String getNodeType() {
        return "KNOWLEDGE";
    }

    @Override
    public Mono<NodeExecutionResult> execute(WorkflowContext context, DagModel.DagNode node) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();

            Map<String, Object> nodeData = node.getData() != null ? node.getData() : new HashMap<>();
            String queryTpl = MapUtil.getStr(nodeData, "query", "{{query}}");
            int topK = MapUtil.getInt(nodeData, "topK", 5);
            double minScore = MapUtil.getDouble(nodeData, "minScore", 0.45);

            // 解析数据集 ID 列表
            List<Long> datasetIds = new ArrayList<>();
            Object datasetIdsObj = nodeData.get("datasetIds");
            if (datasetIdsObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Number n) {
                        datasetIds.add(n.longValue());
                    } else if (item instanceof String s) {
                        datasetIds.add(Long.valueOf(s));
                    }
                }
            } else if (datasetIdsObj instanceof String s && s.startsWith("[")) {
                datasetIds = JSON.parseArray(s, Long.class);
            }

            // 1. 变量插值
            String resolvedQuery = context.resolveTemplate(queryTpl);

            // 2. 调用 RAG 三路混合检索服务
            List<Citation> citations = ragService.retrieveCitations(datasetIds, resolvedQuery, topK, minScore);
            String formattedContext = Citation.formatPromptContext(citations);

            // 3. 沉淀至上下文变量池
            context.setVariable(node.getId() + ".context", formattedContext);
            context.setVariable(node.getId() + ".citations", citations);
            context.setVariable("rag_context", formattedContext);

            Map<String, Object> outputs = new HashMap<>();
            outputs.put("context", formattedContext);
            outputs.put("citationCount", citations.size());
            outputs.put("citations", citations);

            long duration = System.currentTimeMillis() - startTime;
            return NodeExecutionResult.success(node.getId(), getNodeType(), outputs, duration);
        });
    }
}

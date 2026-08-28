package com.agentforge.tools.builtin;

import com.agentforge.service.rag.RagService;
import com.agentforge.tools.BaseTool;
import com.agentforge.vo.Citation;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库混合检索工具 (供 ReAct Agent 智能体动态调用)
 */
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool implements BaseTool {

    private final RagService ragService;

    @Override
    public String getName() {
        return "knowledge_search";
    }

    @Override
    public String getDescription() {
        return "企业私有知识库混合检索工具，用于从内部规章制度、技术文档、业务文档中查找精准信息。入参：{\"datasetIds\": [1], \"query\": \"检索问题\"}";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> props = new HashMap<>();

        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "要从知识库中检索的问题描述");
        props.put("query", queryProp);

        Map<String, Object> idsProp = new HashMap<>();
        idsProp.put("type", "array");
        idsProp.put("description", "指定查询的知识库 ID 数组");
        props.put("datasetIds", idsProp);

        schema.put("properties", props);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        if (params == null || !params.containsKey("query")) {
            return "错误：缺少 query 检索参数";
        }
        String query = String.valueOf(params.get("query"));
        List<Long> datasetIds = new ArrayList<>();
        Object idsObj = params.get("datasetIds");
        if (idsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number n) {
                    datasetIds.add(n.longValue());
                } else if (item != null) {
                    datasetIds.add(Long.valueOf(item.toString()));
                }
            }
        } else if (idsObj != null) {
            try {
                datasetIds = JSON.parseArray(idsObj.toString(), Long.class);
            } catch (Exception ignored) {
            }
        }

        List<Citation> citations = ragService.retrieveCitations(datasetIds, query, 3, 0.45);
        return Citation.formatPromptContext(citations);
    }
}

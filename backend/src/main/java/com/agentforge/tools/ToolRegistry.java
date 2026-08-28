package com.agentforge.tools;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态工具注册中心
 * 管理所有内置工具与外部 OpenAPI 3.0 动态导入工具，并生成 ReAct 提示词描述
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final List<BaseTool> builtInTools;

    /**
     * 获取指定名称的工具实例
     */
    public BaseTool getTool(String name) {
        if (name == null) return null;
        return builtInTools.stream()
                .filter(t -> t.getName().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 列出所有可用工具清单
     */
    public List<BaseTool> listAllTools() {
        return new ArrayList<>(builtInTools);
    }

    /**
     * 执行指定工具
     */
    public String executeTool(String toolName, Map<String, Object> params) {
        BaseTool tool = getTool(toolName);
        if (tool == null) {
            return "错误：未找到名为 [" + toolName + "] 的工具";
        }
        try {
            return tool.execute(params != null ? params : new HashMap<>());
        } catch (Exception e) {
            log.error("工具执行异常: toolName={}, error={}", toolName, e.getMessage());
            return "工具执行异常: " + e.getMessage();
        }
    }

    /**
     * 生成供 ReAct Agent 使用的系统提示词工具描述列表
     */
    public String generateToolsPrompt(List<String> enabledToolNames) {
        List<BaseTool> activeTools = builtInTools;
        if (enabledToolNames != null && !enabledToolNames.isEmpty()) {
            activeTools = builtInTools.stream()
                    .filter(t -> enabledToolNames.contains(t.getName()))
                    .toList();
        }

        if (activeTools.isEmpty()) {
            return "【当前未启用任何外部工具】";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("你可以使用以下工具来解决问题：\n\n");

        for (BaseTool tool : activeTools) {
            sb.append(String.format("### %s\n- 功能描述：%s\n- 入参格式 (JSON Schema)：%s\n\n",
                    tool.getName(),
                    tool.getDescription(),
                    JSON.toJSONString(tool.getParametersSchema())
            ));
        }

        sb.append("""
                【ReAct 交互格式规范】
                请严格按照以下步骤进行推理和工具调度（每轮只能产生一个 Action）：
                
                Thought: 思考当前需要做什么，是否需要调用工具
                Action: 要调用的工具名称 (必须是上述列表中的一个)
                Action Input: 严格合法的 JSON 格式工具入参字典
                
                当你从 Observation 获得足够信息，或者无需调用工具时，请直接给出最终结论：
                Final Answer: 对用户问题的完整、专业、详尽的最终回答
                """);

        return sb.toString();
    }
}

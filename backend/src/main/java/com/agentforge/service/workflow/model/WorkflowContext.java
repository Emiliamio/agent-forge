package com.agentforge.service.workflow.model;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONPath;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流运行时执行上下文 (支持 JSONPath 深层多级变量解析)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作流运行时上下文")
public class WorkflowContext implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_\\.]+)\\}\\}");

    private Long tenantId;
    private Long workflowId;
    private Long executionId;

    @Builder.Default
    private Map<String, Object> inputs = new ConcurrentHashMap<>();

    @Builder.Default
    private Map<String, Object> outputs = new ConcurrentHashMap<>();

    @Builder.Default
    private Map<String, Object> variables = new ConcurrentHashMap<>();

    @Builder.Default
    private List<NodeStepLog> stepLogs = Collections.synchronizedList(new ArrayList<>());

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeStepLog implements Serializable {
        private static final long serialVersionUID = 1L;

        private String nodeId;
        private String nodeName;
        private String nodeType;
        private String status; // SUCCESS, FAILED, SKIPPED
        private Map<String, Object> inputSnapshot;
        private Object outputSnapshot;
        private long durationMs;
        private String errorMsg;
        private long timestamp;
    }

    public void setVariable(String key, Object value) {
        if (key != null && value != null) {
            variables.put(key, value);
        }
    }

    public Object getVariable(String key) {
        if (key == null) return null;
        if (variables.containsKey(key)) {
            return variables.get(key);
        }
        return inputs.get(key);
    }

    /**
     * 替换模板字符串中的占位符，支持 {{var}} 与 {{node_id.json.data.user_id}} 深层 JSONPath 解析
     */
    public String resolveTemplate(String template) {
        if (template == null || !template.contains("{{")) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            Object resolvedValue = resolveExpression(expr);
            String replacement = resolvedValue != null ? String.valueOf(resolvedValue) : "";
            // 防止 replacement 中包含 $ 和 \ 导致 appendReplacement 报错
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 解析具体表达式 (如 "http_1.json.data.name" 或 "username")
     */
    private Object resolveExpression(String expr) {
        // 1. 直接命中变量池
        if (variables.containsKey(expr)) {
            return variables.get(expr);
        }
        if (inputs.containsKey(expr)) {
            return inputs.get(expr);
        }

        // 2. 解析点号路径 (如 node_1.json.data.userId)
        if (expr.contains(".")) {
            String[] parts = expr.split("\\.", 2);
            String rootKey = parts[0];
            String path = "$." + parts[1];

            Object rootObj = variables.get(rootKey);
            if (rootObj == null) {
                rootObj = inputs.get(rootKey);
            }

            if (rootObj != null) {
                try {
                    String jsonStr = rootObj instanceof String ? (String) rootObj : JSON.toJSONString(rootObj);
                    Object val = JSONPath.eval(jsonStr, path);
                    if (val != null) {
                        return val;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return null;
    }
}

package com.agentforge.service.workflow.node;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * CONDITION 条件分支判断节点执行器
 * 支持 ==, !=, >, <, contains, isEmpty 等规则判断，并输出目标分支 (true/false)
 */
@Slf4j
@Component
public class ConditionNodeExecutor implements WorkflowNodeExecutor {

    @Override
    public String getNodeType() {
        return "CONDITION";
    }

    @Override
    public Mono<NodeExecutionResult> execute(WorkflowContext context, DagModel.DagNode node) {
        long startTime = System.currentTimeMillis();

        Map<String, Object> nodeData = node.getData() != null ? node.getData() : new HashMap<>();
        String variableName = MapUtil.getStr(nodeData, "variable", "");
        String operator = MapUtil.getStr(nodeData, "operator", "==");
        String targetValue = MapUtil.getStr(nodeData, "value", "");

        // 获取变量实际值
        Object actualValObj = context.getVariable(variableName);
        String actualValue = actualValObj != null ? String.valueOf(actualValObj) : "";
        String resolvedTargetValue = context.resolveTemplate(targetValue);

        boolean isMatched = evaluateCondition(actualValue, operator, resolvedTargetValue);
        String selectedHandle = isMatched ? "true" : "false";

        context.setVariable(node.getId() + ".result", isMatched);
        context.setVariable(node.getId() + ".branch", selectedHandle);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("matched", isMatched);
        outputs.put("branch", selectedHandle);

        long duration = System.currentTimeMillis() - startTime;
        NodeExecutionResult result = NodeExecutionResult.success(node.getId(), getNodeType(), outputs, duration);
        result.setSelectedNextHandle(selectedHandle);

        return Mono.just(result);
    }

    private boolean evaluateCondition(String actual, String op, String expected) {
        if (op == null) return false;
        switch (op.toLowerCase()) {
            case "==", "equals", "eq" -> {
                return StrUtil.equals(actual, expected);
            }
            case "!=", "not_equals", "neq" -> {
                return !StrUtil.equals(actual, expected);
            }
            case "contains" -> {
                return StrUtil.contains(actual, expected);
            }
            case "not_contains" -> {
                return !StrUtil.contains(actual, expected);
            }
            case "is_empty", "empty" -> {
                return StrUtil.isBlank(actual);
            }
            case "is_not_empty", "not_empty" -> {
                return StrUtil.isNotBlank(actual);
            }
            case ">", "gt" -> {
                try {
                    return Double.parseDouble(actual) > Double.parseDouble(expected);
                } catch (Exception e) {
                    return false;
                }
            }
            case "<", "lt" -> {
                try {
                    return Double.parseDouble(actual) < Double.parseDouble(expected);
                } catch (Exception e) {
                    return false;
                }
            }
            case ">=", "gte" -> {
                try {
                    return Double.parseDouble(actual) >= Double.parseDouble(expected);
                } catch (Exception e) {
                    return false;
                }
            }
            case "<=", "lte" -> {
                try {
                    return Double.parseDouble(actual) <= Double.parseDouble(expected);
                } catch (Exception e) {
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
    }
}

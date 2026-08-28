package com.agentforge.tools.builtin;

import cn.hutool.core.util.NumberUtil;
import com.agentforge.tools.BaseTool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置数学计算器工具
 */
@Component
public class CalculatorTool implements BaseTool {

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "精准数学计算器，用于执行加减乘除、幂运算与高精度科学计算表达式。入参示例：{\"expression\": \"128 * 45 + 3.14\"}";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> props = new HashMap<>();
        Map<String, Object> exprProp = new HashMap<>();
        exprProp.put("type", "string");
        exprProp.put("description", "需要计算的数学表达式字符串，如 256 * 18 / 3");
        props.put("expression", exprProp);

        schema.put("properties", props);
        schema.put("required", List.of("expression"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        if (params == null || !params.containsKey("expression")) {
            return "错误：缺少待计算的 expression 参数";
        }
        String expr = String.valueOf(params.get("expression")).trim();
        try {
            // 支持四则运算与括号解析
            double result = NumberUtil.calculate(expr);
            return String.format("计算表达式 [%s] 的精准结果为: %s", expr, NumberUtil.toStr(result));
        } catch (Exception e) {
            return "计算失败：" + e.getMessage();
        }
    }
}

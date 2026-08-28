package com.agentforge.service.agent.llm.json;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 大模型截断与语法破损 JSON 栈式智能修复引擎 (Json Repair Engine)
 * 解决大模型在 Token 上限耗尽时返回半截 JSON (缺少反引号、缺少右花括号) 导致 JSON.parse 崩溃的问题
 */
@Slf4j
@Component
public class JsonRepairEngine {

    /**
     * 自动修复残缺 JSON 字符串并安全反序列化为 JSONObject
     */
    public JSONObject parseAndRepair(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new JSONObject();
        }

        // 1. 去除 Markdown 代码块标记
        String text = raw.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        text = text.trim();

        // 2. 尝试标准解析
        try {
            return JSON.parseObject(text);
        } catch (Exception ignored) {
            // 解析失败，启动栈式自动补全修复算法
        }

        // 3. 栈式修复
        String repaired = repairJsonString(text);
        try {
            return JSON.parseObject(repaired);
        } catch (Exception e) {
            log.warn("JSON 修复后仍无法解析，返回兜底对象: raw={}, repaired={}, error={}", raw, repaired, e.getMessage());
            JSONObject fallback = new JSONObject();
            fallback.put("rawContent", raw);
            return fallback;
        }
    }

    /**
     * 栈式状态机补齐未闭合的双引号、花括号与方括号
     */
    public String repairJsonString(String text) {
        if (StrUtil.isBlank(text)) return "{}";

        StringBuilder sb = new StringBuilder();
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean isEscaped = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\\' && !isEscaped) {
                isEscaped = true;
                sb.append(c);
                continue;
            }

            if (c == '"' && !isEscaped) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{' || c == '[') {
                    stack.push(c);
                } else if (c == '}' && !stack.isEmpty() && stack.peek() == '{') {
                    stack.pop();
                } else if (c == ']' && !stack.isEmpty() && stack.peek() == '[') {
                    stack.pop();
                }
            }

            sb.append(c);
            isEscaped = false;
        }

        // 1. 若字符串处于未闭合状态，自动补全双引号
        if (inString) {
            sb.append('"');
        }

        // 2. 移除尾部多余的逗号 (如 `{"a": 1, ` -> `{"a": 1`)
        String temp = sb.toString().trim();
        if (temp.endsWith(",")) {
            temp = temp.substring(0, temp.length() - 1);
            sb = new StringBuilder(temp);
        }

        // 3. 按照栈内未匹配符号逆序闭合括号
        while (!stack.isEmpty()) {
            char open = stack.pop();
            if (open == '{') {
                sb.append('}');
            } else if (open == '[') {
                sb.append(']');
            }
        }

        return sb.toString();
    }
}

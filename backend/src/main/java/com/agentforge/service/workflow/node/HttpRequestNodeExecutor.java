package com.agentforge.service.workflow.node;

import cn.hutool.core.map.MapUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP / Webhook 外部请求节点执行器
 */
@Slf4j
@Component
public class HttpRequestNodeExecutor implements WorkflowNodeExecutor {

    @Override
    public String getNodeType() {
        return "HTTP";
    }

    @Override
    public Mono<NodeExecutionResult> execute(WorkflowContext context, DagModel.DagNode node) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();

            Map<String, Object> nodeData = node.getData() != null ? node.getData() : new HashMap<>();
            String urlTpl = MapUtil.getStr(nodeData, "url", "");
            String methodStr = MapUtil.getStr(nodeData, "method", "GET").toUpperCase();
            String bodyTpl = MapUtil.getStr(nodeData, "body", "");
            int timeout = MapUtil.getInt(nodeData, "timeout", 10000);

            String resolvedUrl = context.resolveTemplate(urlTpl);
            String resolvedBody = context.resolveTemplate(bodyTpl);

            Method method = Method.valueOf(methodStr);
            HttpRequest request = HttpRequest.of(resolvedUrl).method(method).timeout(timeout);

            if (!resolvedBody.isBlank() && (method == Method.POST || method == Method.PUT || method == Method.PATCH)) {
                request.body(resolvedBody);
                request.header("Content-Type", "application/json;charset=UTF-8");
            }

            Map<String, Object> outputs = new HashMap<>();
            try (HttpResponse response = request.execute()) {
                int status = response.getStatus();
                String body = response.body();

                outputs.put("status", status);
                outputs.put("body", body);

                // 尝试解析为 JSON 对象便于后续节点取字段
                try {
                    Object jsonBody = JSON.parse(body);
                    outputs.put("json", jsonBody);
                    context.setVariable(node.getId() + ".json", jsonBody);
                } catch (Exception ignored) {
                }

                context.setVariable(node.getId() + ".body", body);
                context.setVariable(node.getId() + ".status", status);

                long duration = System.currentTimeMillis() - startTime;
                return NodeExecutionResult.success(node.getId(), getNodeType(), outputs, duration);
            } catch (Exception e) {
                log.error("HTTP 节点请求失败: url={}", resolvedUrl, e);
                long duration = System.currentTimeMillis() - startTime;
                return NodeExecutionResult.fail(node.getId(), getNodeType(), "HTTP 请求失败: " + e.getMessage(), duration);
            }
        });
    }
}

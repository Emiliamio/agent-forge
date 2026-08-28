package com.agentforge.tools.openapi;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import com.agentforge.tools.BaseTool;
import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 动态 OpenAPI 3.0 / Swagger 外部工具适配器
 * 支持将企业内部已有微服务 REST 接口一键转换为 AI Agent 可调度的标准工具
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiTool implements BaseTool {

    private String name;
    private String description;
    private Map<String, Object> parametersSchema;
    private String targetUrl;
    private String httpMethod; // GET, POST, PUT, DELETE
    @Builder.Default
    private Map<String, String> defaultHeaders = new HashMap<>();

    @Override
    public String execute(Map<String, Object> params) {
        try {
            Method method = Method.valueOf(httpMethod != null ? httpMethod.toUpperCase() : "GET");
            HttpRequest request = HttpRequest.of(targetUrl).method(method).timeout(15000);

            if (defaultHeaders != null) {
                for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
                    request.header(entry.getKey(), entry.getValue());
                }
            }

            if (method == Method.GET) {
                if (params != null) {
                    params.forEach(request::form);
                }
            } else {
                request.body(JSON.toJSONString(params != null ? params : new HashMap<>()));
                request.header("Content-Type", "application/json;charset=UTF-8");
            }

            try (HttpResponse response = request.execute()) {
                return String.format("HTTP 状态码: %d, 响应报文: %s", response.getStatus(), response.body());
            }
        } catch (Exception e) {
            log.error("OpenAPI 工具调用失败: name={}, url={}, error={}", name, targetUrl, e.getMessage());
            return "OpenAPI 接口调用失败: " + e.getMessage();
        }
    }
}

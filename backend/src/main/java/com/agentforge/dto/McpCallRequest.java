package com.agentforge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP 工具调用请求")
public class McpCallRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String serverName;
    private String toolName;
    private Map<String, Object> arguments;
}

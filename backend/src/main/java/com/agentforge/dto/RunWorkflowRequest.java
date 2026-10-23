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
@Schema(description = "运行工作流请求")
public class RunWorkflowRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, Object> inputs;
}

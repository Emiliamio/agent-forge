package com.agentforge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作流定义保存请求")
public class WorkflowDefinitionRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long appId;

    @NotBlank(message = "工作流名称不能为空")
    private String name;
    private String description;
    private String dagJson;
    private Integer version;
    private Integer status;
}

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
@Schema(description = "智能体对话请求对象")
public class AgentChatRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户提问不能为空")
    @Schema(description = "用户提问内容", example = "你好，请介绍一下你自己")
    private String message;

    @Builder.Default
    @Schema(description = "ReAct 最大推理循环轮数", example = "5")
    private int maxIterations = 5;
}

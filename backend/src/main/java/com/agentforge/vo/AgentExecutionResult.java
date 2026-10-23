package com.agentforge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "智能体执行推理结果")
public class AgentExecutionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String finalAnswer;
    private List<ReActStep> steps;
    private List<Citation> citations;
    private int totalTokens;
    private long durationMs;
}

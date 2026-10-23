package com.agentforge.vo;

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
@Schema(description = "ReAct 推理步骤")
public class ReActStep implements Serializable {
    private static final long serialVersionUID = 1L;

    private int stepIndex;
    private String thought;
    private String action;
    private Map<String, Object> actionInput;
    private String observation;
    private long durationMs;
}

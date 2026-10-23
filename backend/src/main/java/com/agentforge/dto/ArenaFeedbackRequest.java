package com.agentforge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模型竞技场反馈请求")
public class ArenaFeedbackRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String battleId;
    private String preferredModel;
    private String comment;
}

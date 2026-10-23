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
@Schema(description = "评价反馈请求")
public class FeedbackRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer rating;
    private String feedbackType;
    private String comment;
}

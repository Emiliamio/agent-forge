package com.agentforge.vo;

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
@Schema(description = "行业模板视图对象")
public class IndustryTemplateVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String templateId;
    private String name;
    private String category;
    private String icon;
    private String description;
    private String valueProposition;
    private String estimatedPrice;
    private String dagJson;
    private String defaultPrompt;
}

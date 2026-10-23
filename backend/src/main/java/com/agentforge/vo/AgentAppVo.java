package com.agentforge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "智能体应用视图对象")
public class AgentAppVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private String avatar;
    private String appType;
    private String systemPrompt;
    private String modelConfig;
    private String toolsConfig;
    private String datasetIds;
    private Integer status;
    private Integer publicEnabled;
    private String publicToken;
    private String publicTokenPrev;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

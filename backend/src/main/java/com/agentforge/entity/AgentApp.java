package com.agentforge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 智能体应用实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("agent_app")
public class AgentApp extends BaseEntity {

    private Long tenantId;

    private String name;

    private String description;

    private String avatar;

    private String appType; // CHATBOT, AGENT, WORKFLOW

    private String systemPrompt;

    private String modelConfig; // JSONB: {"provider": "deepseek", "model": "deepseek-chat", ...}

    private String toolsConfig; // JSONB: ["web_search", "calculator", ...]

    private String datasetIds; // JSONB: [1, 2]

    private Integer status; // 1: 启用, 0: 禁用
}

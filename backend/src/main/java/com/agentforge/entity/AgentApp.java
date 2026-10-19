package com.agentforge.entity;

import com.agentforge.config.mybatis.PgJsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName(value = "agent_app", autoResultMap = true)
public class AgentApp extends BaseEntity {

    private Long tenantId;

    private String name;

    private String description;

    private String avatar;

    private String appType; // CHATBOT, AGENT, WORKFLOW

    private String systemPrompt;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private String modelConfig; // JSONB: {"provider": "deepseek", "model": "deepseek-chat", ...}

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private String toolsConfig; // JSONB: ["web_search", "calculator", ...]

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private String datasetIds; // JSONB: [1, 2]

    private Integer status; // 1: 启用, 0: 禁用
    
    private Integer publicEnabled; // 1: 允许公开免密挂件访问, 0: 禁用
    
    private String publicToken; // 可选的挂件签名密钥/安全分享 Token
}

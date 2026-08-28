package com.agentforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话消息明细实体 (Chat Message)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long sessionId;

    private String role; // user, assistant, system

    private String content;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /**
     * RAG 原文溯源引用 JSON 快照
     */
    private String citationsJson;

    /**
     * ReAct 思考推理步骤 JSON 快照
     */
    private String reasoningStepsJson;

    private LocalDateTime createdAt;
}

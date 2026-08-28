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
 * 问答质量评价与 Bad Case 语料反馈实体 (RLHF / Quality Feedback)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_feedback")
public class ChatFeedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long messageId;

    private Long userId;

    /**
     * 评价打分：1 为 👍 点赞，-1 为 👎 点踩
     */
    private Integer rating;

    /**
     * 反馈标签类型 (如: 答非所问, 幻觉错误, 格式错乱, 缺乏依据)
     */
    private String feedbackType;

    /**
     * 用户详细修改建议或正确答案批注
     */
    private String comment;

    /**
     * 是否已由管理员审核并沉淀入知识库
     */
    @Builder.Default
    private Boolean isResolved = false;

    private LocalDateTime createdAt;
}

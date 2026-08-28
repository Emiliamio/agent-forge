package com.agentforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Token 计量审计与计费日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("token_usage_log")
public class TokenUsageLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long appId;

    private Long userId;

    private Long apiKeyId;

    private String modelName;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Boolean isCached; // 是否命中语义向量缓存

    private BigDecimal costAmount; // 计费金额(元)

    private Long durationMs;

    private LocalDateTime createdAt;
}

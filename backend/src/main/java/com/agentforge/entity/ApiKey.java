package com.agentforge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 租户 API Key 实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("api_key")
public class ApiKey extends BaseEntity {

    private Long tenantId;

    private String name;

    private String keyPrefix; // 如 af-sk-demo

    @JsonIgnore
    private String keyHash; // SHA-256 密文哈希

    private Integer rateLimitRpm; // 每分钟请求数限制

    private Integer rateLimitTpm; // 每分钟 Token 数限制

    private Integer status; // 1: 启用, 0: 禁用

    private LocalDateTime expiredAt;
}

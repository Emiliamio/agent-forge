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
 * 公开挂件调用审计日志实体
 * 平台级审计表，记录谁在何时调用了哪个 agent 及结果（IP/UA 仅存哈希，不含明文）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("widget_call_audit")
public class WidgetCallAudit implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Long agentId;

    private String ipHash;

    private String uaHash;

    private String outcome;

    private Integer httpStatus;

    private Integer latencyMs;

    private LocalDateTime createdAt;
}

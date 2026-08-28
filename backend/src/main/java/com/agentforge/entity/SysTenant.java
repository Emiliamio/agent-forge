package com.agentforge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 租户实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_tenant", autoResultMap = true)
public class SysTenant extends BaseEntity {

    private String name;

    private String code;

    private BigDecimal walletBalance;

    private String whiteLabelConfig; // JSONB 格式

    private Integer status; // 1: 正常, 0: 禁用, 2: 欠费冻结
}

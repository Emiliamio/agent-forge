package com.agentforge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户与当前租户信息 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "当前用户信息与租户白牌配置")
public class UserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "角色")
    private String role;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "租户钱包余额")
    private BigDecimal walletBalance;

    @Schema(description = "租户白牌配置 (JSON 格式：包含 logo, title, copyright)")
    private String whiteLabelConfig;
}

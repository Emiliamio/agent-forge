package com.agentforge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录成功返回结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应数据")
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "认证 Token (Bearer)")
    private String token;

    @Schema(description = "Token 头部标识", example = "Bearer")
    private String tokenPrefix;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户角色 (OWNER/ADMIN/EDITOR/VIEWER)")
    private String role;
}

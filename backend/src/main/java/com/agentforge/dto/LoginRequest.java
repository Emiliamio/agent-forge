package com.agentforge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求 DTO
 */
@Data
@Schema(description = "用户登录请求")
public class LoginRequest {

    @Schema(description = "租户编码 (不填默认为 tenant_default)", example = "tenant_default")
    private String tenantCode;

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "admin123")
    private String password;
}

package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.agentforge.dto.LoginRequest;
import com.agentforge.entity.SysTenant;
import com.agentforge.entity.SysUser;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.SysTenantMapper;
import com.agentforge.mapper.SysUserMapper;
import com.agentforge.vo.LoginVO;
import com.agentforge.vo.Result;
import com.agentforge.vo.UserInfoVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 身份认证与用户权限接口
 */
@Tag(name = "02. 身份认证与权限", description = "提供多租户登录、退出与当前用户信息获取")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;

    @Operation(summary = "用户登录 (支持多租户)")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        String tenantCode = StrUtil.isNotBlank(request.getTenantCode()) ? request.getTenantCode() : "tenant_default";

        // 1. 查询租户
        SysTenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<SysTenant>()
                        .eq(SysTenant::getCode, tenantCode)
                        .eq(SysTenant::getStatus, 1)
        );
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }

        // 2. 查询用户 (必须在该租户下)
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenant.getId())
                        .eq(SysUser::getUsername, request.getUsername())
        );
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // 3. 校验密码 (支持 BCrypt 或初始明文兼容)
        boolean isMatch = false;
        try {
            isMatch = BCrypt.checkpw(request.getPassword(), user.getPasswordHash());
        } catch (Exception e) {
            // 兼容性校验
            isMatch = request.getPassword().equals(user.getPasswordHash());
        }

        if (!isMatch) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // 4. 执行 Sa-Token 登录，附加租户与角色信息
        StpUtil.login(user.getId(), SaLoginConfig.setExtra("tenantId", tenant.getId()).setExtra("role", user.getRole()));

        LoginVO loginVO = LoginVO.builder()
                .token(StpUtil.getTokenValue())
                .tokenPrefix("Bearer")
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .username(user.getUsername())
                .role(user.getRole())
                .build();

        return Result.success("登录成功", loginVO);
    }

    @Operation(summary = "获取当前用户信息与租户白牌配置")
    @SaCheckLogin
    @GetMapping("/user-info")
    public Result<UserInfoVO> getUserInfo() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        SysTenant tenant = tenantMapper.selectById(user.getTenantId());

        UserInfoVO vo = UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(user.getTenantId())
                .tenantName(tenant != null ? tenant.getName() : "默认租户")
                .walletBalance(tenant != null ? tenant.getWalletBalance() : null)
                .whiteLabelConfig(tenant != null ? tenant.getWhiteLabelConfig() : null)
                .build();

        return Result.success(vo);
    }

    @Operation(summary = "用户登出")
    @SaCheckLogin
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success("已成功退出登录", null);
    }
}

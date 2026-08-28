package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.service.auth.SsoAuthService;
import com.agentforge.vo.LoginVO;
import com.agentforge.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 企业级 SSO 认证与组织架构同步接口
 */
@Tag(name = "10. 企业级 SSO 认证与组织架构同步", description = "提供企微/钉钉/飞书/OIDC 扫码免登与员工列表同步")
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
public class OAuth2SsoController {

    private final SsoAuthService ssoAuthService;

    @Operation(summary = "OAuth2 授权码免密扫码登录")
    @PostMapping("/oauth2/callback")
    public Result<LoginVO> oauthLogin(@RequestBody OAuthLoginRequest req) {
        LoginVO loginVO = ssoAuthService.loginByOAuthCode(req.getProvider(), req.getAuthCode());
        return Result.success("SSO 认证登录成功", loginVO);
    }

    @Operation(summary = "全量同步企业组织架构与员工列表")
    @SaCheckLogin
    @SaCheckRole("ADMIN")
    @PostMapping("/org/sync")
    public Result<Integer> syncOrgUsers(@RequestBody SyncOrgRequest req) {
        Long tenantId = TenantContextHolder.getTenantId();
        int count = ssoAuthService.syncOrganizationUsers(tenantId, req.getUserList());
        return Result.success("组织架构同步成功", count);
    }

    @Data
    public static class OAuthLoginRequest {
        private String provider; // wecom, feishu, dingtalk, oidc
        private String authCode;
    }

    @Data
    public static class SyncOrgRequest {
        private List<SsoAuthService.OrgUserSyncItem> userList;
    }
}

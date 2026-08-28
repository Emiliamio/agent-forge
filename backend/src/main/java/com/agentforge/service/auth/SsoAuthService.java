package com.agentforge.service.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.SysUser;
import com.agentforge.mapper.SysUserMapper;
import com.agentforge.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 企业级 SSO 单点登录 (OAuth2 / OIDC / 企微 / 钉钉 / 飞书) 与组织架构同步服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoAuthService {

    private final SysUserMapper userMapper;

    @Data
    @Builder
    public static class OrgDeptSyncItem {
        private Long externalDeptId;
        private String name;
        private Long parentId;
    }

    @Data
    @Builder
    public static class OrgUserSyncItem {
        private String externalUserId;
        private String username;
        private String realName;
        private String email;
        private String phone;
        private Long deptId;
        private String role; // ADMIN, EDITOR, VIEWER
    }

    /**
     * OAuth2 授权码换取免密登录凭证 (支持企微、飞书、钉钉、OIDC)
     */
    public LoginVO loginByOAuthCode(String provider, String authCode) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 模拟 OAuth2 Token 交换并获取外部用户信息
        String externalOpenId = provider + "_user_" + Math.abs(authCode.hashCode() % 10000);
        String username = externalOpenId;

        // 查询或创建系统用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenantId)
                        .eq(SysUser::getUsername, username)
        );

        if (user == null) {
            user = SysUser.builder()
                    .tenantId(tenantId)
                    .username(username)
                    .realName("SSO 认证员工 (" + provider.toUpperCase() + ")")
                    .role("VIEWER")
                    .status(1)
                    .build();
            userMapper.insert(user);
        }

        // Sa-Token 登录
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        log.info("🔐 企业 SSO 扫码免登成功: provider={}, userId={}, username={}", provider, user.getId(), username);

        return LoginVO.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .tenantId(tenantId)
                .build();
    }

    /**
     * 全量同步企业组织架构员工列表 (从企微/钉钉/LDAP 同步)
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncOrganizationUsers(Long tenantId, List<OrgUserSyncItem> userList) {
        if (userList == null || userList.isEmpty()) {
            return 0;
        }

        int syncedCount = 0;
        for (OrgUserSyncItem item : userList) {
            SysUser existing = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getTenantId, tenantId)
                            .eq(SysUser::getUsername, item.getUsername())
            );

            if (existing == null) {
                SysUser newUser = SysUser.builder()
                        .tenantId(tenantId)
                        .username(item.getUsername())
                        .realName(item.getRealName())
                        .email(item.getEmail())
                        .role(item.getRole() != null ? item.getRole() : "VIEWER")
                        .status(1)
                        .build();
                userMapper.insert(newUser);
                syncedCount++;
            } else {
                existing.setRealName(item.getRealName());
                existing.setEmail(item.getEmail());
                userMapper.updateById(existing);
                syncedCount++;
            }
        }

        log.info("🏢 组织架构用户同步完成: tenantId={}, 共同步 {} 名员工", tenantId, syncedCount);
        return syncedCount;
    }
}

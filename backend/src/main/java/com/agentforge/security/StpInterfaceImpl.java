package com.agentforge.security;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.StrUtil;
import com.agentforge.entity.SysUser;
import com.agentforge.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token RBAC 权限与角色数据加载接口
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysUserMapper userMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> roles = getRoleList(loginId, loginType);
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> permissions = new ArrayList<>();
        String primaryRole = roles.get(0);

        switch (primaryRole.toUpperCase()) {
            case "OWNER" -> permissions.add("*"); // 超级管理员拥有所有权限
            case "ADMIN" -> {
                permissions.add("tenant:read");
                permissions.add("user:*");
                permissions.add("dataset:*");
                permissions.add("agent:*");
                permissions.add("workflow:*");
                permissions.add("billing:*");
                permissions.add("apikey:*");
            }
            case "EDITOR" -> {
                permissions.add("dataset:read");
                permissions.add("dataset:write");
                permissions.add("agent:read");
                permissions.add("agent:write");
                permissions.add("workflow:read");
                permissions.add("workflow:write");
                permissions.add("apikey:read");
            }
            case "VIEWER" -> {
                permissions.add("dataset:read");
                permissions.add("agent:read");
                permissions.add("workflow:read");
            }
            default -> permissions.add("common:read");
        }
        return permissions;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null) {
            return Collections.emptyList();
        }
        try {
            Long userId = Long.valueOf(loginId.toString());
            SysUser user = userMapper.selectById(userId);
            if (user != null && StrUtil.isNotBlank(user.getRole())) {
                return List.of(user.getRole());
            }
        } catch (Exception ignored) {
        }
        return List.of("VIEWER");
    }
}

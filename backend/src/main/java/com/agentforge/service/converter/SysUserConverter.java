package com.agentforge.service.converter;

import com.agentforge.entity.SysUser;
import com.agentforge.vo.SysUserVo;

public final class SysUserConverter {

    private SysUserConverter() {}

    public static SysUserVo toVo(SysUser entity) {
        if (entity == null) {
            return null;
        }
        return SysUserVo.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .username(entity.getUsername())
                .passwordHash(entity.getPasswordHash())
                .salt(entity.getSalt())
                .realName(entity.getRealName())
                .email(entity.getEmail())
                .role(entity.getRole())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

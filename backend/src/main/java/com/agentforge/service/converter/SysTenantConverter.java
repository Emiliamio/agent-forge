package com.agentforge.service.converter;

import com.agentforge.entity.SysTenant;
import com.agentforge.vo.SysTenantVo;

public final class SysTenantConverter {

    private SysTenantConverter() {}

    public static SysTenantVo toVo(SysTenant entity) {
        if (entity == null) {
            return null;
        }
        return SysTenantVo.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(entity.getCode())
                .walletBalance(entity.getWalletBalance())
                .whiteLabelConfig(entity.getWhiteLabelConfig())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

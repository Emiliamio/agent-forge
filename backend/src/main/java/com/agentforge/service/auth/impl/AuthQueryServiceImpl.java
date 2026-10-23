package com.agentforge.service.auth.impl;

import com.agentforge.entity.SysTenant;
import com.agentforge.entity.SysUser;
import com.agentforge.mapper.SysTenantMapper;
import com.agentforge.mapper.SysUserMapper;
import com.agentforge.service.auth.AuthQueryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 身份认证与权限查询门面服务实现
 */
@Service
@RequiredArgsConstructor
public class AuthQueryServiceImpl implements AuthQueryService {

    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;

    @Override
    public SysTenant findTenantByCode(String code) {
        return tenantMapper.selectOne(
                new LambdaQueryWrapper<SysTenant>()
                        .eq(SysTenant::getCode, code)
                        .eq(SysTenant::getStatus, 1)
        );
    }

    @Override
    public SysTenant findTenantById(Long id) {
        return tenantMapper.selectById(id);
    }

    @Override
    public SysUser findUserByTenantAndUsername(Long tenantId, String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenantId)
                        .eq(SysUser::getUsername, username)
        );
    }

    @Override
    public SysUser findUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public com.agentforge.vo.SysTenantVo findTenantVoByCode(String code) {
        return com.agentforge.service.converter.SysTenantConverter.toVo(findTenantByCode(code));
    }

    @Override
    public com.agentforge.vo.SysTenantVo findTenantVoById(Long id) {
        return com.agentforge.service.converter.SysTenantConverter.toVo(findTenantById(id));
    }

    @Override
    public com.agentforge.vo.SysUserVo findUserVoByTenantAndUsername(Long tenantId, String username) {
        return com.agentforge.service.converter.SysUserConverter.toVo(findUserByTenantAndUsername(tenantId, username));
    }

    @Override
    public com.agentforge.vo.SysUserVo findUserVoById(Long id) {
        return com.agentforge.service.converter.SysUserConverter.toVo(findUserById(id));
    }
}

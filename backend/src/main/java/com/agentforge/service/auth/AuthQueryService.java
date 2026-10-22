package com.agentforge.service.auth;

import com.agentforge.entity.SysTenant;
import com.agentforge.entity.SysUser;

/**
 * 身份认证与权限查询门面服务
 */
public interface AuthQueryService {

    SysTenant findTenantByCode(String code);

    SysTenant findTenantById(Long id);

    SysUser findUserByTenantAndUsername(Long tenantId, String username);

    SysUser findUserById(Long id);
}

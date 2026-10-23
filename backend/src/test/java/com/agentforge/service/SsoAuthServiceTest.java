package com.agentforge.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.SysUser;
import com.agentforge.mapper.SysUserMapper;
import com.agentforge.service.platform.api.SsoAuthService;
import com.agentforge.vo.LoginVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SsoAuthServiceTest: 企业 SSO 免密登入与组织架构同步单测")
public class SsoAuthServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private SsoAuthService ssoAuthService;

    @BeforeEach
    void setUp() {
        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("Authorization");
        config.setTokenPrefix("Bearer");
        config.setIsReadCookie(false);
        config.setIsReadHeader(true);
        SaManager.setConfig(config);
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        SaManager.setSaTokenContext(new cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet());

        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        try {
            if (StpUtil.isLogin()) {
                StpUtil.logout();
            }
        } catch (Exception ignored) {
        }
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("loginByOAuthCode: 新用户免登自动建档分支")
    void testLoginByOAuthCode_NewUserCreated() {
        when(userMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(1001L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        LoginVO vo = ssoAuthService.loginByOAuthCode("wechat", "auth_code_9999");
        assertNotNull(vo);
        assertNotNull(vo.getToken());
        assertTrue(vo.getUsername().startsWith("wechat_user_"));
        assertEquals("VIEWER", vo.getRole());
        assertEquals(1L, vo.getTenantId());

        verify(userMapper, times(1)).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("loginByOAuthCode: 既有用户免登复用分支")
    void testLoginByOAuthCode_ExistingUser() {
        SysUser existing = SysUser.builder()
                .tenantId(1L)
                .username("feishu_user_1234")
                .realName("既有飞书员工")
                .role("ADMIN")
                .status(1)
                .build();
        existing.setId(2002L);
        when(userMapper.selectOne(any())).thenReturn(existing);

        LoginVO vo = ssoAuthService.loginByOAuthCode("feishu", "auth_code_1234");
        assertNotNull(vo);
        assertNotNull(vo.getToken());
        assertEquals("feishu_user_1234", vo.getUsername());
        assertEquals("ADMIN", vo.getRole());
        assertEquals(1L, vo.getTenantId());

        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("syncOrganizationUsers: 空列表返回 0")
    void testSyncOrganizationUsers_NullOrEmpty() {
        assertEquals(0, ssoAuthService.syncOrganizationUsers(1L, null));
        assertEquals(0, ssoAuthService.syncOrganizationUsers(1L, Collections.emptyList()));
    }

    @Test
    @DisplayName("syncOrganizationUsers: 混合新用户与既有用户更新同步")
    void testSyncOrganizationUsers_NewAndExistingUsers() {
        SsoAuthService.OrgUserSyncItem newUserItem = SsoAuthService.OrgUserSyncItem.builder()
                .externalUserId("ext_01")
                .username("employee_new")
                .realName("新入职员工")
                .email("new@company.com")
                .phone("13800000001")
                .deptId(10L)
                .role(null) // 预期默认 VIEWER
                .build();

        SsoAuthService.OrgUserSyncItem existingUserItem = SsoAuthService.OrgUserSyncItem.builder()
                .externalUserId("ext_02")
                .username("employee_exist")
                .realName("调岗员工")
                .email("exist@company.com")
                .phone("13800000002")
                .deptId(11L)
                .role("EDITOR")
                .build();

        SysUser existingDbUser = SysUser.builder()
                .tenantId(1L)
                .username("employee_exist")
                .realName("旧名字")
                .email("old@company.com")
                .role("VIEWER")
                .status(1)
                .build();
        existingDbUser.setId(5005L);

        when(userMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(existingDbUser);

        int count = ssoAuthService.syncOrganizationUsers(1L, List.of(newUserItem, existingUserItem));
        assertEquals(2, count);

        verify(userMapper, times(1)).insert(argThat((SysUser u) ->
                "employee_new".equals(u.getUsername()) && "VIEWER".equals(u.getRole())));
        verify(userMapper, times(1)).updateById(argThat((SysUser u) ->
                "employee_exist".equals(u.getUsername()) && "调岗员工".equals(u.getRealName())));
    }

    @Test
    @DisplayName("syncOrganizationUsers: 异常传播分支")
    void testSyncOrganizationUsers_MapperThrowsException() {
        SsoAuthService.OrgUserSyncItem item = SsoAuthService.OrgUserSyncItem.builder()
                .username("error_user")
                .build();
        when(userMapper.selectOne(any())).thenThrow(new RuntimeException("数据库写入超时"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                ssoAuthService.syncOrganizationUsers(1L, List.of(item)));
        assertTrue(ex.getMessage().contains("数据库写入超时"));
    }

    @Test
    @DisplayName("DTO/VO 模型覆盖: OrgDeptSyncItem 与 OrgUserSyncItem")
    void testDataTransferObjects() {
        SsoAuthService.OrgDeptSyncItem dept = SsoAuthService.OrgDeptSyncItem.builder()
                .externalDeptId(100L)
                .name("研发体系")
                .parentId(0L)
                .build();
        assertEquals(100L, dept.getExternalDeptId());
        assertEquals("研发体系", dept.getName());
        assertEquals(0L, dept.getParentId());
        assertNotNull(dept.toString());

        SsoAuthService.OrgUserSyncItem userItem = SsoAuthService.OrgUserSyncItem.builder()
                .externalUserId("ext_99")
                .username("user_99")
                .realName("李工")
                .email("li@test.com")
                .phone("13900000000")
                .deptId(100L)
                .role("ADMIN")
                .build();
        assertEquals("ext_99", userItem.getExternalUserId());
        assertEquals("user_99", userItem.getUsername());
        assertEquals("李工", userItem.getRealName());
        assertEquals("li@test.com", userItem.getEmail());
        assertEquals("13900000000", userItem.getPhone());
        assertEquals(100L, userItem.getDeptId());
        assertEquals("ADMIN", userItem.getRole());
        assertNotNull(userItem.toString());
    }
}

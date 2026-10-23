package com.agentforge;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.StpUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.controller.AuthController;
import com.agentforge.dto.LoginRequest;
import com.agentforge.vo.SysTenantVo;
import com.agentforge.vo.SysUserVo;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.service.platform.api.AuthQueryService;
import com.agentforge.security.TenantInterceptor;
import com.agentforge.vo.LoginVO;
import com.agentforge.vo.Result;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("身份认证、密码哈希校验与跨租户越权拦截全链路测试")
public class AuthAndTenantSecurityMvcTest {
    private cn.dev33.satoken.config.SaTokenConfig originalConfig;
    private cn.dev33.satoken.dao.SaTokenDao originalDao;
    private cn.dev33.satoken.context.SaTokenContext originalContext;


    @Mock
    private AuthQueryService authQueryService;

    @InjectMocks
    private AuthController authController;

    private final TenantInterceptor tenantInterceptor = new TenantInterceptor();

    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalDao = SaManager.getSaTokenDao();
        originalContext = SaManager.getSaTokenContext();

        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("Authorization");
        config.setTokenPrefix("Bearer");
        config.setIsReadCookie(false);
        config.setIsReadHeader(true);
        SaManager.setConfig(config);
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request, response)
        );
                cn.dev33.satoken.context.SaHolder.getContext().setContext(
                new cn.dev33.satoken.servlet.model.SaRequestForServlet(request),
                new cn.dev33.satoken.servlet.model.SaResponseForServlet(response),
                new cn.dev33.satoken.servlet.model.SaStorageForServlet(request)
        );
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        cn.dev33.satoken.context.SaHolder.getContext().clearContext();
        if (originalConfig != null) { SaManager.setConfig(originalConfig); }
        if (originalDao != null) { SaManager.setSaTokenDao(originalDao); }
        if (originalContext != null) { SaManager.setSaTokenContext(originalContext); }

        TenantContextHolder.clear();
        try {
            if (StpUtil.isLogin()) {
                StpUtil.logout();
            }
        } catch (Exception ignored) {
        }
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("测试正确的默认管理员账户密码登录成功并颁发 Token")
    void testLoginSuccess() {
        SysTenantVo tenant = SysTenantVo.builder()
                .name("演示租户")
                .code("tenant_default")
                .status(1)
                .build();
        tenant.setId(1L);

        SysUserVo user = SysUserVo.builder()
                .tenantId(1L)
                .username("admin")
                .passwordHash("$2a$10$VnTSObTDaYlHVoAUrU72Tu/ohEH3hdJxAw6VbNHGs1zavk..1wfi6")
                .role("OWNER")
                .status(1)
                .build();
        user.setId(1L);

        when(authQueryService.findTenantVoByCode(any())).thenReturn(tenant);
        when(authQueryService.findUserVoByTenantAndUsername(any(), any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setTenantCode("tenant_default");
        request.setUsername("admin");
        request.setPassword("admin123456");

        Result<LoginVO> result = authController.login(request);
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData().getToken());
        assertEquals(1L, result.getData().getTenantId());
        assertEquals("admin", result.getData().getUsername());
    }

    @Test
    @DisplayName("测试密码错误时抛出 USER_PASSWORD_ERROR")
    void testLoginWrongPassword() {
        SysTenantVo tenant = SysTenantVo.builder()
                .code("tenant_default")
                .status(1)
                .build();
        tenant.setId(1L);

        SysUserVo user = SysUserVo.builder()
                .tenantId(1L)
                .username("admin")
                .passwordHash("$2a$10$VnTSObTDaYlHVoAUrU72Tu/ohEH3hdJxAw6VbNHGs1zavk..1wfi6")
                .status(1)
                .build();
        user.setId(1L);

        when(authQueryService.findTenantVoByCode(any())).thenReturn(tenant);
        when(authQueryService.findUserVoByTenantAndUsername(any(), any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setTenantCode("tenant_default");
        request.setUsername("admin");
        request.setPassword("wrong_password");

        BusinessException ex = assertThrows(BusinessException.class, () -> authController.login(request));
        assertEquals(ErrorCode.USER_PASSWORD_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("测试同一账号连续高频登录尝试超过 5 次后触发防爆破限流 RATE_LIMIT_EXCEEDED")
    void testLoginRateLimitTriggered() {
        LoginRequest request = new LoginRequest();
        request.setTenantCode("tenant_default");
        request.setUsername("brute_force_user");
        request.setPassword("any_password");

        // 前 5 次尝试因租户或密码错误失败，但消耗尝试额度
        for (int i = 0; i < 5; i++) {
            assertThrows(BusinessException.class, () -> authController.login(request));
        }

        // 第 6 次应当直接被限流拦截，不进入后续 DB 查询
        BusinessException ex = assertThrows(BusinessException.class, () -> authController.login(request));
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("防爆破风控保护"));
    }

    @Test
    @DisplayName("测试未登录时即使携带 Header 也严格锁定系统默认租户，拒绝未授权伪造")
    void testUnauthenticatedHeaderTenant() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/auth/login");
        request.addHeader(TenantInterceptor.HEADER_TENANT_ID, "888");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean canHandle = tenantInterceptor.preHandle(request, response, new Object());
        assertTrue(canHandle);
        assertEquals(TenantContextHolder.DEFAULT_TENANT_ID, TenantContextHolder.getTenantId());

        tenantInterceptor.afterCompletion(request, response, new Object(), null);
        assertEquals(TenantContextHolder.DEFAULT_TENANT_ID, TenantContextHolder.getTenantId());
    }

    @Test
    @DisplayName("测试已登录态下恶意篡改 X-Tenant-Id 发生越权时被强制拦截抛出 403 FORBIDDEN")
    void testAuthenticatedCrossTenantSpoofingBlocked() {
        // 模拟已登录租户 1
        StpUtil.login(1001L);
        StpUtil.getSession().set("tenantId", 1L);

        // 攻击者伪造请求头 X-Tenant-Id: 2
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantInterceptor.HEADER_TENANT_ID, "2");
        request.addHeader("Authorization", "Bearer " + StpUtil.getTokenValue());
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request, response)
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                tenantInterceptor.preHandle(request, response, new Object())
        );
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("跨租户安全拦截"));
    }

    @Test
    @DisplayName("测试已登录态下正常访问强制绑定会话真实租户 ID")
    void testAuthenticatedNormalTenantContext() {
        // 模拟已登录租户 100
        StpUtil.login(2002L);
        StpUtil.getSession().set("tenantId", 100L);

        // 用户未带请求头或携带自身相同的租户头
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantInterceptor.HEADER_TENANT_ID, "100");
        request.addHeader("Authorization", "Bearer " + StpUtil.getTokenValue());
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request, response)
        );

        boolean canHandle = tenantInterceptor.preHandle(request, response, new Object());
        assertTrue(canHandle);
        assertEquals(100L, TenantContextHolder.getTenantId());

        tenantInterceptor.afterCompletion(request, response, new Object(), null);
        assertEquals(TenantContextHolder.DEFAULT_TENANT_ID, TenantContextHolder.getTenantId());
    }
}

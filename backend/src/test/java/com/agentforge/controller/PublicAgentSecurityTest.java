package com.agentforge.controller;

import com.agentforge.entity.AgentApp;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.AgentAppMapper;
import com.agentforge.security.ClientIpResolver;
import com.agentforge.service.agent.ReActAgentService;
import com.agentforge.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.agentforge.exception.GlobalExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicAgentController 公开挂件 Security-by-Default 与凭证校验安全测试")
public class PublicAgentSecurityTest {

    @Mock
    private AgentAppMapper agentAppMapper;

    @Mock
    private ReActAgentService reActAgentService;

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private com.agentforge.security.ReplayGuard replayGuard;
    @Mock
    private com.agentforge.security.LuaRateLimiter luaRateLimiter;

    @InjectMocks
    private PublicAgentController publicAgentController;

    @Test
    @DisplayName("Security-by-Default：当 public_enabled 为 0 或 null 时，坚决抛出 403 Forbidden")
    void testPublicDisabledThrowsForbidden() {
        AgentApp app = new AgentApp();
        app.setId(1L);
        app.setTenantId(1L);
        app.setStatus(1);
        app.setPublicEnabled(0); // 默认关闭

        when(agentAppMapper.selectByIdWithoutTenant(1L)).thenReturn(app);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");

        PublicAgentController.WidgetChatRequest req = new PublicAgentController.WidgetChatRequest();
        req.setMessage("您好，请问差旅标准是多少？");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                publicAgentController.widgetChat(1L, req, null, null, null, servletRequest)
        );
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("未开启公开挂件访问权限"));

        // 测试 publicEnabled == null 的边界
        app.setPublicEnabled(null);
        BusinessException exNull = assertThrows(BusinessException.class, () ->
                publicAgentController.widgetChat(1L, req, null, null, null, servletRequest)
        );
        assertEquals(ErrorCode.FORBIDDEN.getCode(), exNull.getCode());
    }

    @Test
    @DisplayName("安全凭据校验：当配置了 public_token 时，未传或传入错误凭据坚决抛出 401/403")
    void testPublicTokenRequiredMismatchThrowsUnauthorized() {
        AgentApp app = new AgentApp();
        app.setId(1L);
        app.setTenantId(1L);
        app.setStatus(1);
        app.setPublicEnabled(1); // 开启
        app.setPublicToken("secret-token-2026");

        when(agentAppMapper.selectByIdWithoutTenant(1L)).thenReturn(app);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");

        PublicAgentController.WidgetChatRequest req = new PublicAgentController.WidgetChatRequest();
        req.setMessage("测试查询");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        // 1. 未传 Token
        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                publicAgentController.widgetChat(1L, req, null, null, null, servletRequest)
        );
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex1.getCode());

        // 2. 传入错误 Token (主头)
        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                publicAgentController.widgetChat(1L, req, "wrong-token", null, null, servletRequest)
        );
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex2.getCode());

        // 3. 传入错误 Token (兼容头)
        BusinessException ex3 = assertThrows(BusinessException.class, () ->
                publicAgentController.widgetChat(1L, req, null, "wrong-token", null, servletRequest)
        );
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex3.getCode());
    }

    @Test
    @DisplayName("凭据常量时间比对通过：支持 X-Public-Token / Authorization 正确执行会话")
    void testPublicTokenSuccess() {
        AgentApp app = new AgentApp();
        app.setId(1L);
        app.setTenantId(1L);
        app.setStatus(1);
        app.setPublicEnabled(1);
        app.setPublicToken("my-secure-token");

        when(agentAppMapper.selectByIdWithoutTenant(1L)).thenReturn(app);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");

        ReActAgentService.AgentExecutionResult mockResult = ReActAgentService.AgentExecutionResult.builder()
                .finalAnswer("北京住宿报销上限为 800 元/天。")
                .totalTokens(128)
                .build();
        when(reActAgentService.runAgent(eq(1L), eq("北京住宿"), anyInt())).thenReturn(mockResult);

        PublicAgentController.WidgetChatRequest req = new PublicAgentController.WidgetChatRequest();
        req.setMessage("北京住宿");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        // 1. 通过 X-Public-Token 请求头验证
        Result<ReActAgentService.AgentExecutionResult> res1 = publicAgentController.widgetChat(
                1L, req, "my-secure-token", null, null, servletRequest
        );
        assertNotNull(res1);
        assertEquals(200, res1.getCode());
        assertEquals("北京住宿报销上限为 800 元/天。", res1.getData().getFinalAnswer());

        // 2. 通过 X-AgentForge-Public-Token 兼容头验证
        Result<ReActAgentService.AgentExecutionResult> res2 = publicAgentController.widgetChat(
                1L, req, null, "my-secure-token", null, servletRequest
        );
        assertNotNull(res2);
        assertEquals(200, res2.getCode());
        assertEquals("北京住宿报销上限为 800 元/天。", res2.getData().getFinalAnswer());
    }

    @Test
    @DisplayName("智能体不存在或停用时抛出 404/AGENT_APP_NOT_FOUND")
    void testAgentNotFoundThrowsException() {
        when(agentAppMapper.selectByIdWithoutTenant(999L)).thenReturn(null);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");

        PublicAgentController.WidgetChatRequest req = new PublicAgentController.WidgetChatRequest();
        req.setMessage("test");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                publicAgentController.widgetChat(999L, req, null, null, null, servletRequest)
        );
        assertEquals(ErrorCode.AGENT_APP_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("请求入参校验：未申报的 prompt 别名入参必须触发校验失败抛出 400/PARAM_ERROR")
    void testPromptAliasRejectedAndThrowsParamError() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(publicAgentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/public/agents/1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("安全开关控制：当 legacy-token-enabled 为 false 时明文拒绝 401，而签名令牌依然 200")
    void testLegacyTokenSwitchDisabledRejectsPlaintextAllowsSigned() {
        publicAgentController.setLegacyTokenEnabled(false);

        AgentApp app = new AgentApp();
        app.setId(1L);
        app.setTenantId(1L);
        app.setStatus(1);
        app.setPublicEnabled(1);
        app.setPublicToken("secret-rotation-key-2026");

        when(agentAppMapper.selectByIdWithoutTenant(1L)).thenReturn(app);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");

        PublicAgentController.WidgetChatRequest req = new PublicAgentController.WidgetChatRequest();
        req.setMessage("测试明文被拒");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        // 1. 明文令牌在开关关闭时坚决抛出 401
        BusinessException ex = assertThrows(BusinessException.class, () ->
                publicAgentController.widgetChat(1L, req, "secret-rotation-key-2026", null, null, servletRequest)
        );
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("明文令牌已停用"));

        // 2. 相同场景下 v1 签名令牌依然正常放行 (HTTP 200)
        long exp = (System.currentTimeMillis() / 1000L) + 300L;
        String signedToken = com.agentforge.security.SignedWidgetToken.sign("secret-rotation-key-2026", 1L, 1L, exp, "nonce-switch-test-1");

        when(reActAgentService.runAgent(eq(1L), anyString(), anyInt()))
                .thenReturn(ReActAgentService.AgentExecutionResult.builder().finalAnswer("放行回答").build());

        Result<ReActAgentService.AgentExecutionResult> res = publicAgentController.widgetChat(
                1L, req, signedToken, null, null, servletRequest
        );
        assertNotNull(res);
        assertEquals(200, res.getCode());
    }

    @Test
    @DisplayName("安全开关控制：当 legacy-token-enabled 为 true 时明文令牌正常兼容放行")
    void testLegacyTokenSwitchEnabledAllowsPlaintext() {
        publicAgentController.setLegacyTokenEnabled(true);

        AgentApp app = new AgentApp();
        app.setId(1L);
        app.setTenantId(1L);
        app.setStatus(1);
        app.setPublicEnabled(1);
        app.setPublicToken("secret-rotation-key-2026");

        when(agentAppMapper.selectByIdWithoutTenant(1L)).thenReturn(app);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");
        when(reActAgentService.runAgent(eq(1L), anyString(), anyInt()))
                .thenReturn(ReActAgentService.AgentExecutionResult.builder().finalAnswer("明文放行回答").build());

        PublicAgentController.WidgetChatRequest req = new PublicAgentController.WidgetChatRequest();
        req.setMessage("测试明文兼容");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        Result<ReActAgentService.AgentExecutionResult> res = publicAgentController.widgetChat(
                1L, req, "secret-rotation-key-2026", null, null, servletRequest
        );
        assertNotNull(res);
        assertEquals(200, res.getCode());
    }

    @Test
    @DisplayName("密钥轮换：轮换前签发令牌在轮换后 (prev 生效期内) 仍为 200，清空 prev 后该令牌立即 401")
    void testSecretRotationTwoPhaseVerification() {
        publicAgentController.setLegacyTokenEnabled(true);

        AgentApp app = new AgentApp();
        app.setId(1L);
        app.setTenantId(1L);
        app.setStatus(1);
        app.setPublicEnabled(1);
        app.setPublicToken("new-secret-2026");
        app.setPublicTokenPrev("old-secret-2025");

        when(agentAppMapper.selectByIdWithoutTenant(1L)).thenReturn(app);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");
        when(reActAgentService.runAgent(eq(1L), anyString(), anyInt()))
                .thenReturn(ReActAgentService.AgentExecutionResult.builder().finalAnswer("旧密钥依然有效").build());

        // 使用 old-secret-2025 签发令牌
        long exp = (System.currentTimeMillis() / 1000L) + 300L;
        String oldSignedToken = com.agentforge.security.SignedWidgetToken.sign("old-secret-2025", 1L, 1L, exp, "nonce-rot-1");

        PublicAgentController.WidgetChatRequest req = new PublicAgentController.WidgetChatRequest();
        req.setMessage("测试轮换过渡期");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        // 1. 在 prev 生效期内，old 令牌依然能成功通过验签并执行 (HTTP 200)
        Result<ReActAgentService.AgentExecutionResult> res = publicAgentController.widgetChat(
                1L, req, oldSignedToken, null, null, servletRequest
        );
        assertNotNull(res);
        assertEquals(200, res.getCode());

        // 2. 清空 prev (第二阶段收敛)，同一令牌必须坚决被拒 (HTTP 401)
        app.setPublicTokenPrev(null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                publicAgentController.widgetChat(1L, req, oldSignedToken, null, null, servletRequest)
        );
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }
}

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
import org.springframework.mock.web.MockHttpServletRequest;

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
                publicAgentController.widgetChat(1L, req, null, null, servletRequest)
        );
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("未开启公开挂件访问权限"));

        // 测试 publicEnabled == null 的边界
        app.setPublicEnabled(null);
        BusinessException exNull = assertThrows(BusinessException.class, () ->
                publicAgentController.widgetChat(1L, req, null, null, servletRequest)
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
                publicAgentController.widgetChat(1L, req, null, null, servletRequest)
        );
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex1.getCode());

        // 2. 传入错误 Token
        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                publicAgentController.widgetChat(1L, req, "wrong-token", null, servletRequest)
        );
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex2.getCode());
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

        // 通过 X-Public-Token 请求头验证
        Result<ReActAgentService.AgentExecutionResult> res = publicAgentController.widgetChat(
                1L, req, "my-secure-token", null, servletRequest
        );

        assertNotNull(res);
        assertEquals(200, res.getCode());
        assertEquals("北京住宿报销上限为 800 元/天。", res.getData().getFinalAnswer());
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
                publicAgentController.widgetChat(999L, req, null, null, servletRequest)
        );
        assertEquals(ErrorCode.AGENT_APP_NOT_FOUND.getCode(), ex.getCode());
    }
}

package com.agentforge.controller;

import com.agentforge.entity.WidgetCallAudit;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.exception.GlobalExceptionHandler;
import com.agentforge.mapper.WidgetCallAuditMapper;
import com.agentforge.security.ClientIpResolver;
import com.agentforge.security.WidgetCallAuditFilter;
import com.agentforge.service.agent.AgentAppService;
import com.agentforge.service.agent.ReActAgentService;
import com.agentforge.vo.AgentAppVo;
import com.agentforge.vo.AgentExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicAgentAuditTest: 挂件调用全量审计、脱敏与异常隔离测试")
public class PublicAgentAuditTest {

    @Mock
    private AgentAppService agentAppService;

    @Mock
    private ReActAgentService reActAgentService;

    @Mock
    private WidgetCallAuditMapper widgetCallAuditMapper;

    @Mock
    private ClientIpResolver clientIpResolver;

    @InjectMocks
    private PublicAgentController publicAgentController;

    private MockMvc mockMvc;

    // 直接同步调用的 Executor，便于单测精确断言 Mock 调用
    private final Executor directExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        WidgetCallAuditFilter auditFilter = new WidgetCallAuditFilter(
                widgetCallAuditMapper,
                clientIpResolver,
                directExecutor
        );

        mockMvc = MockMvcBuilders.standaloneSetup(publicAgentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(auditFilter)
                .build();
    }

    @Test
    @DisplayName("L2 载体测试 - 正常调用：状态码 200，审计写入 outcome=ok 且 ip_hash 为 64 位 SHA-256")
    void testAuditRecordSuccess() throws Exception {
        AgentAppVo app = new AgentAppVo();
        app.setId(1L);
        app.setTenantId(1L);
        app.setStatus(1);
        app.setPublicEnabled(1);
        app.setPublicToken(null);
        when(agentAppService.findPublicAppVo(1L)).thenReturn(app);

        AgentExecutionResult execResult = AgentExecutionResult.builder().finalAnswer("Hello from agent").build();
        when(reActAgentService.runAgent(eq(1L), any(), anyInt())).thenReturn(execResult);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("192.168.1.100");

        mockMvc.perform(post("/public/agents/1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "TestBrowser/1.0")
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<WidgetCallAudit> captor = ArgumentCaptor.forClass(WidgetCallAudit.class);
        verify(widgetCallAuditMapper, times(1)).insert(captor.capture());

        WidgetCallAudit saved = captor.getValue();
        assertEquals(1L, saved.getAgentId());
        assertEquals("ok", saved.getOutcome());
        assertEquals(200, saved.getHttpStatus());
        assertTrue(saved.getLatencyMs() >= 0);
        assertNotNull(saved.getIpHash());
        assertTrue(saved.getIpHash().matches("^[0-9a-f]{64}$"));
        assertFalse(saved.getIpHash().contains("192.168.1.100"));
        assertNotNull(saved.getUaHash());
        assertTrue(saved.getUaHash().matches("^[0-9a-f]{64}$"));
    }

    @Test
    @DisplayName("L1 反向验证 - 审计写库抛异常时业务零影响：业务仍 200 OK，审计异常被捕获")
    void testAuditWriteFailureDoesNotBreakBusiness() throws Exception {
        AgentAppVo app = new AgentAppVo();
        app.setId(1L);
        app.setTenantId(1L);
        app.setStatus(1);
        app.setPublicEnabled(1);
        when(agentAppService.findPublicAppVo(1L)).thenReturn(app);

        AgentExecutionResult execResult = AgentExecutionResult.builder().finalAnswer("Hello safe").build();
        when(reActAgentService.runAgent(eq(1L), any(), anyInt())).thenReturn(execResult);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");

        // 模拟数据库故障写入抛出异常
        doThrow(new RuntimeException("Database down simulated")).when(widgetCallAuditMapper).insert(any(WidgetCallAudit.class));

        // 必须仍返回 HTTP 200 OK
        mockMvc.perform(post("/public/agents/1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"safe hello\"}"))
                .andExpect(status().isOk());

        verify(widgetCallAuditMapper, times(1)).insert(any(WidgetCallAudit.class));
    }

    @Test
    @DisplayName("L2 载体测试 - 权限拒绝：未开启公开访问，记录 outcome=invalid_token 且状态 403")
    void testAuditRecordForbidden() throws Exception {
        AgentAppVo app = new AgentAppVo();
        app.setId(2L);
        app.setTenantId(1L);
        app.setStatus(1);
        app.setPublicEnabled(0); // 未开启
        when(agentAppService.findPublicAppVo(2L)).thenReturn(app);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("10.0.0.5");

        mockMvc.perform(post("/public/agents/2/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"test forbidden\"}"))
                .andExpect(status().isForbidden());

        ArgumentCaptor<WidgetCallAudit> captor = ArgumentCaptor.forClass(WidgetCallAudit.class);
        verify(widgetCallAuditMapper, times(1)).insert(captor.capture());

        WidgetCallAudit saved = captor.getValue();
        assertEquals(2L, saved.getAgentId());
        assertEquals("invalid_token", saved.getOutcome());
        assertEquals(403, saved.getHttpStatus());
    }

    @Test
    @DisplayName("L2 载体测试 - 参数非法：Agent 不存在，记录 outcome=bad_request 且状态 404")
    void testAuditRecordNotFound() throws Exception {
        when(agentAppService.findPublicAppVo(999L)).thenReturn(null);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");

        mockMvc.perform(post("/public/agents/999/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"test not found\"}"))
                .andExpect(status().isNotFound());

        ArgumentCaptor<WidgetCallAudit> captor = ArgumentCaptor.forClass(WidgetCallAudit.class);
        verify(widgetCallAuditMapper, times(1)).insert(captor.capture());

        WidgetCallAudit saved = captor.getValue();
        assertEquals(999L, saved.getAgentId());
        assertEquals("bad_request", saved.getOutcome());
        assertEquals(404, saved.getHttpStatus());
    }
}

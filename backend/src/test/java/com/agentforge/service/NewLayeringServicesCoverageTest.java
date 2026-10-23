package com.agentforge.service;

import com.agentforge.context.TenantContextHolder;
import com.agentforge.dto.*;
import com.agentforge.entity.*;
import com.agentforge.exception.BusinessException;
import com.agentforge.mapper.*;
import com.agentforge.service.agent.ReActAgentService;
import com.agentforge.service.agent.internal.AgentAppServiceImpl;
import com.agentforge.service.platform.internal.AuthQueryServiceImpl;
import com.agentforge.service.billing.internal.BillingServiceImpl;
import com.agentforge.service.chat.internal.ChatSessionServiceImpl;
import com.agentforge.service.security.api.PiiMaskingService;
import com.agentforge.service.workflow.internal.WorkflowEngine;
import com.agentforge.service.workflow.internal.WorkflowServiceImpl;
import com.agentforge.service.workflow.internal.WorkflowContext;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("新增门面服务层单元测试与代码覆盖守护")
public class NewLayeringServicesCoverageTest {

    @Mock private AgentAppMapper agentAppMapper;
    @Mock private WorkflowDefinitionMapper workflowDefinitionMapper;
    @Mock private WorkflowExecutionMapper workflowExecutionMapper;
    @Mock private WorkflowEngine workflowEngine;
    @Mock private SysTenantMapper tenantMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private TokenUsageLogMapper tokenUsageLogMapper;
    @Mock private ChatSessionMapper chatSessionMapper;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private ChatFeedbackMapper chatFeedbackMapper;
    @Mock private ReActAgentService reActAgentService;
    @Mock private PiiMaskingService piiMaskingService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("测试 AgentAppServiceImpl 全分支覆盖")
    void testAgentAppServiceImpl() {
        AgentAppServiceImpl service = new AgentAppServiceImpl(agentAppMapper);

        // createAgent with blank appType
        AgentApp app1 = new AgentApp();
        app1.setName("agent-1");
        service.createAgent(app1, 1L);
        assertEquals(1L, app1.getTenantId());
        assertEquals("AGENT", app1.getAppType());
        assertEquals(1, app1.getStatus());

        // createAgent with custom appType
        AgentApp app2 = new AgentApp();
        app2.setName("agent-2");
        app2.setAppType("CHATBOT");
        service.createAgent(app2, 1L);
        assertEquals("CHATBOT", app2.getAppType());

        // listAgents
        when(agentAppMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        assertNotNull(service.listAgents(1L, "kw", 1, 10));
        assertNotNull(service.listAgents(1L, null, 1, 10));

        // getAgent success
        AgentApp found = new AgentApp();
        found.setId(10L);
        found.setTenantId(1L);
        when(agentAppMapper.selectById(10L)).thenReturn(found);
        assertEquals(10L, service.getAgent(10L, 1L).getId());

        // getAgent null or tenant mismatch
        assertThrows(BusinessException.class, () -> service.getAgent(99L, 1L));
        assertThrows(BusinessException.class, () -> service.getAgent(10L, 2L));

        // findPublicApp
        when(agentAppMapper.selectByIdWithoutTenant(10L)).thenReturn(found);
        assertEquals(10L, service.findPublicApp(10L).getId());

        // VO methods
        assertNotNull(service.createAgent(new AgentAppRequest("test", "desc", "av", "AGENT", "sp", "{}", "[]", "[]", 1, 1, "tok", "prev"), 1L));
        assertNotNull(service.listAgentsVo(1L, "kw", 1, 10));
        assertNotNull(service.getAgentVo(10L, 1L));
        assertNotNull(service.findPublicAppVo(10L));
    }

    @Test
    @DisplayName("测试 WorkflowServiceImpl 全分支覆盖")
    void testWorkflowServiceImpl() {
        WorkflowServiceImpl service = new WorkflowServiceImpl(workflowDefinitionMapper, workflowExecutionMapper, workflowEngine);

        // saveWorkflow 新建
        WorkflowDefinition defNew = new WorkflowDefinition();
        defNew.setName("new-flow");
        service.saveWorkflow(defNew, 1L);
        assertEquals(1, defNew.getVersion());
        assertEquals(1, defNew.getStatus());

        // saveWorkflow 更新成功
        WorkflowDefinition existing = new WorkflowDefinition();
        existing.setId(5L);
        existing.setTenantId(1L);
        existing.setVersion(2);
        when(workflowDefinitionMapper.selectById(5L)).thenReturn(existing);

        WorkflowDefinition defUpdate = new WorkflowDefinition();
        defUpdate.setId(5L);
        service.saveWorkflow(defUpdate, 1L);
        assertEquals(3, defUpdate.getVersion());

        // saveWorkflow 租户不匹配或不存在
        WorkflowDefinition defAlien = new WorkflowDefinition();
        defAlien.setId(5L);
        assertThrows(BusinessException.class, () -> service.saveWorkflow(defAlien, 99L));
        defAlien.setId(999L);
        assertThrows(BusinessException.class, () -> service.saveWorkflow(defAlien, 1L));

        // getWorkflow
        assertEquals(5L, service.getWorkflow(5L, 1L).getId());
        assertThrows(BusinessException.class, () -> service.getWorkflow(5L, 99L));
        assertThrows(BusinessException.class, () -> service.getWorkflow(999L, 1L));

        // runWorkflow: 租户不匹配或不存在
        assertThrows(BusinessException.class, () -> service.runWorkflow(5L, 99L, Map.of()));
        assertThrows(BusinessException.class, () -> service.runWorkflow(999L, 1L, Map.of()));

        // runWorkflow: 节点为空
        existing.setDagJson("{\"nodes\":[]}");
        assertThrows(BusinessException.class, () -> service.runWorkflow(5L, 1L, Map.of()));

        // runWorkflow: 成功
        existing.setDagJson("{\"nodes\":[{\"id\":\"n1\",\"type\":\"Start\"}]}");
        when(workflowEngine.executeWorkflow(any(), any())).thenReturn(
                Mono.just(WorkflowContext.builder().outputs(Map.of("res", "done")).stepLogs(List.of()).build())
        );
        WorkflowExecution execSuccess = service.runWorkflow(5L, 1L, Map.of("key", "val"));
        assertEquals("SUCCESS", execSuccess.getStatus());

        // runWorkflow: 引擎执行抛错
        when(workflowEngine.executeWorkflow(any(), any())).thenReturn(Mono.error(new RuntimeException("Engine error")));
        assertThrows(BusinessException.class, () -> service.runWorkflow(5L, 1L, null));

        // VO methods
        when(workflowEngine.executeWorkflow(any(), any())).thenReturn(
                Mono.just(WorkflowContext.builder().outputs(Map.of("res", "done")).stepLogs(List.of()).build())
        );
        WorkflowDefinitionRequest req = WorkflowDefinitionRequest.builder()
                .name("vo-flow")
                .description("desc")
                .dagJson("{}")
                .version(1)
                .status(1)
                .build();
        assertNotNull(service.saveWorkflowVo(req, 1L));
        assertNotNull(service.getWorkflowVo(5L, 1L));
        assertNotNull(service.runWorkflowVo(5L, 1L, Map.of()));
    }

    @Test
    @DisplayName("测试 AuthQueryServiceImpl 全查询覆盖")
    void testAuthQueryServiceImpl() {
        AuthQueryServiceImpl service = new AuthQueryServiceImpl(tenantMapper, userMapper);

        when(tenantMapper.selectOne(any())).thenReturn(new SysTenant());
        assertNotNull(service.findTenantByCode("code"));

        when(tenantMapper.selectById(1L)).thenReturn(new SysTenant());
        assertNotNull(service.findTenantById(1L));

        when(userMapper.selectOne(any())).thenReturn(new SysUser());
        assertNotNull(service.findUserByTenantAndUsername(1L, "user"));

        when(userMapper.selectById(1L)).thenReturn(new SysUser());
        assertNotNull(service.findUserById(1L));

        assertNotNull(service.findTenantVoById(1L));
        assertNotNull(service.findTenantVoByCode("code"));
        assertNotNull(service.findUserVoById(1L));
        assertNotNull(service.findUserVoByTenantAndUsername(1L, "user"));
    }

    @Test
    @DisplayName("测试 BillingServiceImpl 与 ChatSessionServiceImpl 扩展方法覆盖")
    void testBillingAndChatSessionExtensions() {
        BillingServiceImpl billingService = new BillingServiceImpl(tenantMapper, tokenUsageLogMapper);

        // getBillingStats: 租户不存在
        assertThrows(BusinessException.class, () -> billingService.getBillingStats(99L));

        // getBillingStats: 成功
        SysTenant tenant = new SysTenant();
        tenant.setName("tenant-1");
        tenant.setWalletBalance(new BigDecimal("200.00"));
        when(tenantMapper.selectById(1L)).thenReturn(tenant);
        when(tokenUsageLogMapper.selectCount(any())).thenReturn(10L);
        Map<String, Object> stats = billingService.getBillingStats(1L);
        assertEquals("tenant-1", stats.get("tenantName"));
        assertEquals(10L, stats.get("totalCalls"));

        // listUsageLogs
        when(tokenUsageLogMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        assertNotNull(billingService.listUsageLogs(1L, 1, 10));

        // recharge
        assertThrows(BusinessException.class, () -> billingService.recharge(99L, BigDecimal.TEN));
        assertThrows(BusinessException.class, () -> billingService.recharge(1L, BigDecimal.ZERO));
        assertThrows(BusinessException.class, () -> billingService.recharge(1L, new BigDecimal("-5")));
        BigDecimal updated = billingService.recharge(1L, new BigDecimal("50.00"));
        assertEquals(new BigDecimal("250.00"), updated);
        assertNotNull(billingService.listUsageLogsVo(1L, 1, 10));

        // ChatSessionServiceImpl.listUserSessions
        ChatSessionServiceImpl chatSessionService = new ChatSessionServiceImpl(
                chatSessionMapper, chatMessageMapper, chatFeedbackMapper, reActAgentService, piiMaskingService
        );
        when(chatSessionMapper.selectList(any())).thenReturn(List.of(new ChatSession()));
        assertEquals(1, chatSessionService.listUserSessions(1L, 1L).size());
        assertEquals(1, chatSessionService.listUserSessionVos(1L, 1L).size());

        when(chatMessageMapper.selectList(any())).thenReturn(List.of(new ChatMessage()));
        assertEquals(1, chatSessionService.listMessageVos(1L).size());
        assertNotNull(chatSessionService.createOrGetSessionVo(10L, 1L, "title"));
    }
}

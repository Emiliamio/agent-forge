package com.agentforge;

import cn.dev33.satoken.secure.BCrypt;
import com.agentforge.dto.LoginRequest;
import com.agentforge.entity.AgentApp;
import com.agentforge.entity.SysUser;
import com.agentforge.mapper.AgentAppMapper;
import com.agentforge.mapper.SysUserMapper;
import com.agentforge.service.agent.ReActAgentService;
import com.agentforge.support.LiveTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实安全边界与访问控制矩阵 16 行全覆盖测试
 * 覆盖：401 匿名/伪造拦截、403 越权/禁用拦截、404 挂件不存在拦截、429 超频限流拦截
 */
@EnabledIf("com.agentforge.support.LiveTestSupport#usable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = "agentforge.widget.rate-limit-rpm=3")
@DisplayName("AuthzMatrixTest: 真实安全边界与鉴权矩阵 16 行全覆盖测试")
public class AuthzMatrixTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        LiveTestSupport.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private AgentAppMapper agentAppMapper;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ReActAgentService reActAgentService;

    private static final String FAKE_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.fake_tampered_token";
    private static String cachedAdminToken;
    private static String cachedViewerToken;

    @BeforeEach
    void setUp() throws Exception {
        // 1. 确保普通租户用户 (role: VIEWER, tenantId: 1) 存在于数据库
        SysUser viewer = userMapper.selectById(2L);
        if (viewer == null) {
            viewer = new SysUser();
            viewer.setId(2L);
            viewer.setTenantId(1L);
            viewer.setUsername("viewer_emp");
            viewer.setPasswordHash(BCrypt.hashpw("viewer123456"));
            viewer.setSalt("af_salt_test");
            viewer.setRealName("普通员工");
            viewer.setEmail("viewer@agentforge.ai");
            viewer.setRole("VIEWER");
            viewer.setStatus(1);
            userMapper.insert(viewer);
        }

        // 2. 通过真实登录端点换取 Token 并缓存
        if (cachedAdminToken == null) {
            cachedAdminToken = obtainToken("admin", "admin123456");
        }
        if (cachedViewerToken == null) {
            cachedViewerToken = obtainToken("viewer_emp", "viewer123456");
        }

        // 3. 确保 Agent 1 (public_enabled=1) 与 Agent 2 (public_enabled=0) 存在
        AgentApp app1 = agentAppMapper.selectByIdWithoutTenant(1L);
        if (app1 != null && (app1.getPublicEnabled() == null || app1.getPublicEnabled() != 1)) {
            app1.setPublicEnabled(1);
            agentAppMapper.updateById(app1);
        }

        AgentApp app2 = agentAppMapper.selectByIdWithoutTenant(2L);
        if (app2 == null) {
            app2 = new AgentApp();
            app2.setId(2L);
            app2.setTenantId(1L);
            app2.setName("未启用挂件的智能体");
            app2.setDescription("测试专用");
            app2.setAppType("CHAT");
            app2.setStatus(1);
            app2.setPublicEnabled(0);
            agentAppMapper.insert(app2);
        } else if (app2.getPublicEnabled() != null && app2.getPublicEnabled() != 0) {
            app2.setPublicEnabled(0);
            agentAppMapper.updateById(app2);
        }

        // 4. 清理限流测试可能残留的 Redis Key
        if (redisTemplate != null) {
            redisTemplate.delete("widget:ratelimit:198.51.100.88");
            redisTemplate.delete("widget:ratelimit:127.0.0.1");
        }

        // 5. Mock ReActAgentService 避免外部模型调用
        when(reActAgentService.runAgent(any(), any(), anyInt()))
                .thenReturn(ReActAgentService.AgentExecutionResult.builder()
                        .finalAnswer("模拟挂件回复")
                        .totalTokens(10)
                        .build());
    }

    private String obtainToken(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setTenantCode("tenant_default");
        req.setUsername(username);
        req.setPassword(password);

        MvcResult mvcResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return root.path("data").path("token").asText();
    }

    private void assertSecurityResponse(MvcResult result, int expectedHttpStatus, int expectedBizCode) throws Exception {
        int actualStatus = result.getResponse().getStatus();
        assertEquals(expectedHttpStatus, actualStatus, "HTTP Status 应当精确返回 " + expectedHttpStatus);

        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNotNull(content, "响应体不能为空");
        assertFalse(content.isBlank(), "响应体不能为空字符串");

        JsonNode root = objectMapper.readTree(content);
        assertTrue(root.has("code"), "响应体必须包含业务状态码字段 'code'");
        assertEquals(expectedBizCode, root.get("code").asInt(), "业务状态码 code 应当精确返回 " + expectedBizCode);

        // 防敏感信息泄漏断言
        assertFalse(content.contains("SQLException"), "绝不向客户端泄漏底层 SQL 异常");
        assertFalse(content.contains("Exception at "), "绝不向客户端泄漏 Java 堆栈");
        assertFalse(content.contains("sys_user"), "绝不泄漏内部核心表名");
        assertFalse(content.contains("password_hash"), "绝不泄漏密码凭证列名");
    }

    // =========================================================================
    // 16 行鉴权矩阵单测覆盖
    // =========================================================================

    @Test
    @DisplayName("01. GET /agents (无 Authorization) -> 401 匿名拦截")
    void test01_GetAgents_NoAuth_Returns401() throws Exception {
        MvcResult res = mockMvc.perform(get("/agents"))
                .andReturn();
        assertSecurityResponse(res, 401, 401);
    }

    @Test
    @DisplayName("02. GET /agents (伪造 Token) -> 401 伪造拦截")
    void test02_GetAgents_FakeToken_Returns401() throws Exception {
        MvcResult res = mockMvc.perform(get("/agents")
                        .header("Authorization", "Bearer " + FAKE_TOKEN))
                .andReturn();
        assertSecurityResponse(res, 401, 401);
    }

    @Test
    @DisplayName("03. POST /rag/search (无 Authorization) -> 401 匿名拦截")
    void test03_PostRagSearch_NoAuth_Returns401() throws Exception {
        String body = "{\"datasetIds\":[1],\"query\":\"测试\"}";
        MvcResult res = mockMvc.perform(post("/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertSecurityResponse(res, 401, 401);
    }

    @Test
    @DisplayName("04. POST /rag/search (伪造 Token) -> 401 伪造拦截")
    void test04_PostRagSearch_FakeToken_Returns401() throws Exception {
        String body = "{\"datasetIds\":[1],\"query\":\"测试\"}";
        MvcResult res = mockMvc.perform(post("/rag/search")
                        .header("Authorization", "Bearer " + FAKE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertSecurityResponse(res, 401, 401);
    }

    @Test
    @DisplayName("05. GET /rag/chunks/document/1 (无 Authorization) -> 401 匿名拦截")
    void test05_GetRagChunks_NoAuth_Returns401() throws Exception {
        MvcResult res = mockMvc.perform(get("/rag/chunks/document/1"))
                .andReturn();
        assertSecurityResponse(res, 401, 401);
    }

    @Test
    @DisplayName("06. GET /rag/chunks/document/1 (伪造 Token) -> 401 伪造拦截")
    void test06_GetRagChunks_FakeToken_Returns401() throws Exception {
        MvcResult res = mockMvc.perform(get("/rag/chunks/document/1")
                        .header("Authorization", "Bearer " + FAKE_TOKEN))
                .andReturn();
        assertSecurityResponse(res, 401, 401);
    }

    @Test
    @DisplayName("07. GET /datasets (无 Authorization) -> 401 匿名拦截")
    void test07_GetDatasets_NoAuth_Returns401() throws Exception {
        MvcResult res = mockMvc.perform(get("/datasets"))
                .andReturn();
        assertSecurityResponse(res, 401, 401);
    }

    @Test
    @DisplayName("08. GET /datasets (伪造 Token) -> 401 伪造拦截")
    void test08_GetDatasets_FakeToken_Returns401() throws Exception {
        MvcResult res = mockMvc.perform(get("/datasets")
                        .header("Authorization", "Bearer " + FAKE_TOKEN))
                .andReturn();
        assertSecurityResponse(res, 401, 401);
    }

    @Test
    @DisplayName("09. GET /documents/list?datasetId=1 (无 Authorization) -> 401 匿名拦截")
    void test09_GetDocumentsList_NoAuth_Returns401() throws Exception {
        MvcResult res = mockMvc.perform(get("/documents/list?datasetId=1"))
                .andReturn();
        assertSecurityResponse(res, 401, 401);
    }

    @Test
    @DisplayName("10. GET /documents/list?datasetId=1 (伪造 Token) -> 401 伪造拦截")
    void test10_GetDocumentsList_FakeToken_Returns401() throws Exception {
        MvcResult res = mockMvc.perform(get("/documents/list?datasetId=1")
                        .header("Authorization", "Bearer " + FAKE_TOKEN))
                .andReturn();
        assertSecurityResponse(res, 401, 401);
    }

    @Test
    @DisplayName("11. GET /system/diagnostics (无 Authorization) -> 401 匿名拦截")
    void test11_GetDiagnostics_NoAuth_Returns401() throws Exception {
        MvcResult res = mockMvc.perform(get("/system/diagnostics"))
                .andReturn();
        assertSecurityResponse(res, 401, 401);
    }

    @Test
    @DisplayName("12. GET /system/diagnostics (普通租户 Token) -> 403 越权拦截 (需 ADMIN/OWNER)")
    void test12_GetDiagnostics_ViewerToken_Returns403() throws Exception {
        // 普通租户 (VIEWER 角色) 访问系统全维诊断接口返回 403 为预期安全加固设计行为 (该接口严格要求 ADMIN 或 OWNER 角色)
        MvcResult res = mockMvc.perform(get("/system/diagnostics")
                        .header("Authorization", "Bearer " + cachedViewerToken))
                .andReturn();
        assertSecurityResponse(res, 403, 403);
    }

    @Test
    @DisplayName("13. GET /billing/tenant/2/usage (租户 1 Token) -> 403 跨租户横向越权拦截")
    void test13_GetBillingTenantUsage_CrossTenant_Returns403() throws Exception {
        MvcResult res = mockMvc.perform(get("/billing/tenant/2/usage")
                        .header("Authorization", "Bearer " + cachedAdminToken))
                .andReturn();
        assertSecurityResponse(res, 403, 403);
    }

    @Test
    @DisplayName("14. POST /public/agents/99999/chat (不存在的 agent) -> 404 挂件不存在拦截 (code: 3001)")
    void test14_PostPublicChat_NonExistentAgent_Returns404() throws Exception {
        String body = "{\"message\":\"hello\"}";
        MvcResult res = mockMvc.perform(post("/public/agents/99999/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertSecurityResponse(res, 404, 3001);
    }

    @Test
    @DisplayName("15. POST /public/agents/2/chat (未启用挂件的 agent) -> 403 严格拒绝")
    void test15_PostPublicChat_DisabledAgent_Returns403() throws Exception {
        String body = "{\"message\":\"hello\"}";
        MvcResult res = mockMvc.perform(post("/public/agents/2/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertSecurityResponse(res, 403, 403);
    }

    @Test
    @DisplayName("16. POST /public/agents/1/chat (单 IP 超频第 4 次) -> 429 限流命中 + Retry-After 响应头 (测试解耦 3 RPM，生产默认 30)")
    void test16_PostPublicChat_RateLimit_Returns429WithRetryAfter() throws Exception {
        String testIp = "198.51.100.88";
        String body = "{\"message\":\"hello\"}";

        // 测试配置 3 RPM (生产默认 30 RPM)：前 3 次在允许频次内放行
        for (int i = 1; i <= 3; i++) {
            MvcResult res = mockMvc.perform(post("/public/agents/1/chat")
                            .with(request -> {
                                request.setRemoteAddr(testIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
            assertEquals(200, res.getResponse().getStatus(), "前 3 次调用应当放行通过");
        }

        // 第 4 次调用触发限流熔断 (返回 HTTP 429 + code: 1008 + Retry-After: 60)
        MvcResult res4 = mockMvc.perform(post("/public/agents/1/chat")
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andReturn();

        assertSecurityResponse(res4, 429, 1008);
    }
}

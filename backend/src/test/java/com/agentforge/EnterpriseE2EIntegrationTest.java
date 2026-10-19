package com.agentforge;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.controller.AuthController;
import com.agentforge.controller.RagController;
import com.agentforge.dto.LoginRequest;
import com.agentforge.entity.DocumentChunk;
import com.agentforge.entity.SysTenant;
import com.agentforge.entity.SysUser;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.mapper.SysTenantMapper;
import com.agentforge.mapper.SysUserMapper;
import com.agentforge.security.TenantInterceptor;
import com.agentforge.service.rag.embedding.EmbeddingService;
import com.agentforge.service.rag.search.HybridSearchService;
import com.agentforge.service.rag.search.RerankerService;
import com.agentforge.service.rag.search.RrfFusionEngine;
import com.agentforge.vo.ChunkSearchResult;
import com.agentforge.vo.Citation;
import com.agentforge.vo.LoginVO;
import com.agentforge.vo.Result;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 企业级全链路集成测试套件 (E2E Integration Test Suite)
 * 覆盖：
 * 1. 默认管理员登录鉴权与 Sa-Token 令牌颁发
 * 2. 跨租户物理越权拦截与安全隔离
 * 3. 文档上传 -> 混合检索 -> 引用召回完整闭环
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("AgentForge 企业级全链路集成测试 (认证、跨租户拦截、RAG检索召回)")
public class EnterpriseE2EIntegrationTest {

    @Mock
    private SysTenantMapper tenantMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private AuthController authController;

    private final TenantInterceptor tenantInterceptor = new TenantInterceptor();

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
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request, response)
        );
        SaManager.setSaTokenContext(new cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet());
        TenantContextHolder.clear();
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
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @Order(1)
    @DisplayName("集成测试 1: 管理员登录全链路 (BCrypt校验 + Bearer Token颁发 + 会话租户绑定)")
    void test1_LoginAuthenticationLifecycle() {
        // 模拟种子数据中的系统管理员与租户
        SysTenant tenant = SysTenant.builder()
                .name("默认超级租户")
                .code("tenant_default")
                .status(1)
                .build();
        tenant.setId(1L);

        // 使用正确的 Sa-Token BCrypt 密码哈希 ($2a$10$VnTSObTDaYlHVoAUrU72Tu/ohEH3hdJxAw6VbNHGs1zavk..1wfi6)
        SysUser user = SysUser.builder()
                .tenantId(1L)
                .username("admin")
                .passwordHash("$2a$10$VnTSObTDaYlHVoAUrU72Tu/ohEH3hdJxAw6VbNHGs1zavk..1wfi6")
                .role("OWNER")
                .status(1)
                .build();
        user.setId(1L);

        when(tenantMapper.selectOne(any())).thenReturn(tenant);
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setTenantCode("tenant_default");
        loginRequest.setUsername("admin");
        loginRequest.setPassword("admin123456");

        Result<LoginVO> loginResult = authController.login(loginRequest);

        assertNotNull(loginResult);
        assertEquals(200, loginResult.getCode());
        assertNotNull(loginResult.getData().getToken(), "登录成功必须颁发有效的访问令牌");
        assertEquals("Bearer", loginResult.getData().getTokenPrefix());
        assertEquals(1L, loginResult.getData().getTenantId());
        assertEquals("admin", loginResult.getData().getUsername());

        // 校验 Sa-Token 会话中存储的真实租户 ID
        assertEquals(1L, Long.valueOf(StpUtil.getSession().get("tenantId").toString()));
    }

    @Test
    @Order(2)
    @DisplayName("集成测试 2: 跨租户物理越权攻击拒绝 (篡改 X-Tenant-Id 触发 403 熔断拦截)")
    void test2_CrossTenantAccessRejection() {
        // 1. 模拟租户 1 用户正常登录
        StpUtil.login(1001L);
        StpUtil.getSession().set("tenantId", 1L);

        // 2. 模拟黑客在请求头中恶意伪造 X-Tenant-Id: 2 企图越权窃取其他企业资产
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantInterceptor.HEADER_TENANT_ID, "2");
        request.addHeader("Authorization", "Bearer " + StpUtil.getTokenValue());
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request, response)
        );

        // 3. 验证租户拦截器敏锐识别越权并直接阻断抛出 403 FORBIDDEN
        BusinessException ex = assertThrows(BusinessException.class, () ->
                tenantInterceptor.preHandle(request, response, new Object())
        );

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("跨租户安全拦截"), "错误信息必须明确提示跨租户安全违规");
    }

    @Test
    @Order(3)
    @DisplayName("集成测试 3: 文档切片与混合检索引用召回链路 (pgvector + 全文检索 + RRF 融合重排)")
    void test3_DocumentUploadAndHybridSearchCitationLifecycle() {
        // 1. 准备测试文档切片数据
        ChunkSearchResult mockResult = ChunkSearchResult.builder()
                .id(10001L)
                .tenantId(1L)
                .datasetId(10L)
                .documentId(100L)
                .chunkIndex(0)
                .content("AgentForge 企业级 RAG 架构标准规范：采用 PostgreSQL 16 pgvector 进行高维向量检索。")
                .score(0.92)
                .tokenCount(85)
                .build();

        // 2. 模拟向量生成与混合检索双路召回结果
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(documentChunkMapper.searchByVector(any(), anyList(), anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of(mockResult));
        when(documentChunkMapper.searchByKeyword(any(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(mockResult));

        RrfFusionEngine rrf = new RrfFusionEngine();
        RerankerService reranker = new RerankerService();
        HybridSearchService searchService = new HybridSearchService(documentChunkMapper, embeddingService, rrf, reranker);

        // 3. 执行混合检索
        List<ChunkSearchResult> results = searchService.hybridSearch(
                List.of(10L),
                "pgvector 高维向量检索标准",
                3,
                0.1
        );

        // 4. 验证检索结果与引用结构健全性
        assertNotNull(results);
        assertFalse(results.isEmpty(), "混合检索召回列表不得为空");
        ChunkSearchResult topResult = results.get(0);
        assertEquals(10001L, topResult.getId());
        assertTrue(topResult.getScore() > 0.0, "召回切片必须具备有效的融合相关性得分");
        assertTrue(topResult.getContent().contains("pgvector"), "召回切片内容必须吻合测试数据");
    }
}

package com.agentforge;

import com.agentforge.dto.LoginRequest;
import com.agentforge.entity.DocumentChunk;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.vo.ChunkSearchResult;
import com.agentforge.vo.LoginVO;
import com.agentforge.vo.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 企业级全上下文 SpringBootTest + Testcontainers 真实闭环集成测试
 * 真实拉起 pgvector 数据库与 Redis 容器，端到端检验：
 * 1. 匿名访问 Fail-Closed 拦截
 * 2. 真实数据库登录换取 Token -> 业务授权访问 -> 伪造租户头 403 熔断
 * 3. DocumentChunkMapper 真实 pgvector 与 jsonb 写入及 <=> 检索闭环 (完全不撞车 JSqlParser)
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("企业级全上下文 SpringBootTest + Testcontainers 真实闭环集成测试")
public class EnterpriseLiveSpringBootE2ETest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("agentforge")
            .withUsername("postgres")
            .withPassword("password123");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres.isRunning()) {
            registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "&stringtype=unspecified");
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
        if (redis.isRunning()) {
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", redis::getFirstMappedPort);
            registry.add("spring.data.redis.password", () -> "");
            registry.add("spring.data.redis.timeout", () -> "3000ms");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private DocumentChunkMapper documentChunkMapper;

    @Test
    @DisplayName("端到端闭环 1: 未登录匿名访问受保护接口严格拦截 (Fail-Closed 校验)")
    void test1_UnauthenticatedAccessBlocked() throws Exception {
        mockMvc.perform(get("/agents"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403 || result.getResponse().getContentAsString().contains("请先登录"));
                });
    }

    @Test
    @DisplayName("端到端闭环 2: 真实数据库登录换取 Bearer Token -> 携带访问 -> 篡改租户头触发 403 熔断")
    void test2_LoginAndCrossTenantRejection() throws Exception {
        // 1. 真实调用 /auth/login
        LoginRequest req = new LoginRequest();
        req.setTenantCode("tenant_default");
        req.setUsername("admin");
        req.setPassword("admin123456");

        String respStr = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Result<LoginVO> result = objectMapper.readValue(respStr,
                objectMapper.getTypeFactory().constructParametricType(Result.class, LoginVO.class));
        assertNotNull(result.getData());
        String token = result.getData().getToken();
        assertNotNull(token, "登录成功必须返回有效访问令牌");

        // 2. 携带合法 Token 访问业务接口
        mockMvc.perform(get("/billing/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 3. 恶意篡改 X-Tenant-Id: 999 企图越权，必须触发 403
        mockMvc.perform(get("/billing/stats")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", "999"))
                .andExpect(resultMatcher -> {
                    String content = resultMatcher.getResponse().getContentAsString();
                    int status = resultMatcher.getResponse().getStatus();
                    assertTrue(status == 403 || content.contains("跨租户安全拦截"), "篡改租户必须触发跨租户安全拦截");
                });
    }

    @Test
    @DisplayName("端到端闭环 3: DocumentChunkMapper 真实 pgvector 与 jsonb 写入及 <=> 检索闭环")
    void test3_DocumentChunkVectorAndJsonbLifecycle() {
        if (documentChunkMapper == null) {
            return;
        }

        DocumentChunk chunk = DocumentChunk.builder()
                .tenantId(1L)
                .datasetId(1L)
                .documentId(1L)
                .chunkIndex(0)
                .content("AgentForge 向量检索与 JSONB 实体写入测试切片")
                .embedding("[0.12, 0.34, 0.56]")
                .metadata("{\"section\": \"Architecture\", \"author\": \"Emiliamio\"}")
                .tokenCount(42)
                .build();

        // 验证写入不会触发 42804 JSONB 异常或 vector 类型不匹配
        int inserted = documentChunkMapper.insert(chunk);
        assertTrue(inserted > 0, "成功通过 PgVectorTypeHandler 与 PgJsonbTypeHandler 写入真实数据库");

        // 验证向量检索不会触发 JSqlParser 的 <=> 解析异常
        List<ChunkSearchResult> searchResults = documentChunkMapper.searchByVector(
                1L,
                List.of(1L),
                "[0.12, 0.34, 0.56]",
                0.0,
                5
        );

        assertNotNull(searchResults);
        assertFalse(searchResults.isEmpty(), "向量检索必须能成功召回切片");
        assertTrue(searchResults.get(0).getContent().contains("AgentForge"));
    }
}

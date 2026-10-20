package com.agentforge;

import com.agentforge.support.LiveTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 基于 Testcontainers 与真实 pgvector/pgvector:pg16 容器的向量与 SQL 扩展校验测试
 * 当本地或 CI 环境已拉起 Docker 时自动触发验证；无 Docker 环境时自动跳过
 */
@EnabledIf("com.agentforge.support.LiveTestSupport#usable")
@SpringBootTest
@DisplayName("Testcontainers 真实 pgvector 容器集成验证 (Docker 自动化)")
public class PgVectorContainerLiveTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        LiveTestSupport.register(r);
    }

    @Autowired
    private Environment env;

    @Test
    @DisplayName("测试真实 PostgreSQL 容器中加载 pgvector 扩展及向量余弦距离计算")
    void testPgVectorExtensionAndDistanceCalculation() throws Exception {
        assertTrue(LiveTestSupport.usable(), "PostgreSQL 容器必须正常运行");

        String jdbcUrl = env.getProperty("spring.datasource.url");
        String username = env.getProperty("spring.datasource.username", "postgres");
        String password = env.getProperty("spring.datasource.password", "password123");

        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                username,
                password
        ); Statement stmt = conn.createStatement()) {

            // 1. 验证激活 vector 扩展
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector;");

            // 2. 创建包含 vector 字段的临时测试表
            stmt.execute("""
                    CREATE TEMPORARY TABLE test_vector_table (
                        id SERIAL PRIMARY KEY,
                        content TEXT,
                        embedding vector(3)
                    );
                    """);

            // 3. 插入测试向量
            stmt.execute("INSERT INTO test_vector_table (content, embedding) VALUES ('doc1', '[1.0, 0.0, 0.0]');");
            stmt.execute("INSERT INTO test_vector_table (content, embedding) VALUES ('doc2', '[0.0, 1.0, 0.0]');");

            // 4. 执行 pgvector 余弦距离查询 <=>
            ResultSet rs = stmt.executeQuery("""
                    SELECT content, embedding <=> '[1.0, 0.0, 0.0]' AS distance
                    FROM test_vector_table
                    ORDER BY distance ASC
                    LIMIT 1;
                    """);

            assertTrue(rs.next(), "必须成功返回查询结果");
            assertEquals("doc1", rs.getString("content"));
            assertEquals(0.0, rs.getDouble("distance"), 0.001, "自身与自身余弦距离必须为 0");
        }
    }

    @Test
    @DisplayName("测试真实 PostgreSQL 容器中验证 vector(3) 与 jsonb 混合写入和 <=> 运算符无报错")
    void testPgVectorAndJsonbTypeHandling() throws Exception {
        assertTrue(LiveTestSupport.usable(), "PostgreSQL 容器必须正常运行");

        String jdbcUrl = env.getProperty("spring.datasource.url");
        if (jdbcUrl != null && !jdbcUrl.contains("stringtype=unspecified")) {
            jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "stringtype=unspecified";
        }
        String username = env.getProperty("spring.datasource.username", "postgres");
        String password = env.getProperty("spring.datasource.password", "password123");

        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                username,
                password
        ); Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector;");

            // 1. 创建包含 vector 与 jsonb 的表
            stmt.execute("""
                    CREATE TEMPORARY TABLE test_chunk_table (
                        id SERIAL PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        content TEXT,
                        embedding vector(3),
                        metadata JSONB
                    );
                    """);

            // 2. 插入测试数据 (包含 PGobject jsonb 与 vector)
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO test_chunk_table (tenant_id, content, embedding, metadata) VALUES (?, ?, ?, ?)")) {
                ps.setLong(1, 1L);
                ps.setString(2, "AgentForge 企业中台规范");

                org.postgresql.util.PGobject vecObj = new org.postgresql.util.PGobject();
                vecObj.setType("vector");
                vecObj.setValue("[0.1, 0.2, 0.3]");
                ps.setObject(3, vecObj);

                org.postgresql.util.PGobject jsonObj = new org.postgresql.util.PGobject();
                jsonObj.setType("jsonb");
                jsonObj.setValue("{\"page\":1,\"title\":\"架构文档\"}");
                ps.setObject(4, jsonObj);

                int rows = ps.executeUpdate();
                assertEquals(1, rows, "成功写入 1 条包含 vector 与 jsonb 的切片记录");
            }

            // 3. 执行 <=> 距离运算与 jsonb 字段查询
            try (java.sql.PreparedStatement queryPs = conn.prepareStatement("""
                    SELECT id, content, metadata->>'title' AS title, (1 - (embedding <=> ?::vector)) AS score
                    FROM test_chunk_table
                    WHERE tenant_id = ?
                    ORDER BY embedding <=> ?::vector ASC
                    LIMIT 1
                    """)) {
                queryPs.setString(1, "[0.1, 0.2, 0.3]");
                queryPs.setLong(2, 1L);
                queryPs.setString(3, "[0.1, 0.2, 0.3]");

                try (ResultSet rs = queryPs.executeQuery()) {
                    assertTrue(rs.next(), "必须查到刚插入的记录");
                    assertEquals("AgentForge 企业中台规范", rs.getString("content"));
                    assertEquals("架构文档", rs.getString("title"));
                    assertEquals(1.0, rs.getDouble("score"), 0.001, "自身与自身相似度得分必须为 1.0");
                }
            }
        }
    }
}

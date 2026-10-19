package com.agentforge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 基于 Testcontainers 与真实 pgvector/pgvector:pg16 容器的向量与 SQL 扩展校验测试
 * 当本地或 CI 环境已拉起 Docker 时自动触发验证；无 Docker 环境时自动跳过
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Testcontainers 真实 pgvector 容器集成验证 (Docker 自动化)")
public class PgVectorContainerLiveTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("agentforge_test")
            .withUsername("test_user")
            .withPassword("test_password_2026");

    @Test
    @DisplayName("测试真实 PostgreSQL 容器中加载 pgvector 扩展及向量余弦距离计算")
    void testPgVectorExtensionAndDistanceCalculation() throws Exception {
        assertTrue(postgres.isRunning(), "PostgreSQL 容器必须正常运行");

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
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
}

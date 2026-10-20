package com.agentforge.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 双通道测试基座：
 *  docker 通道（默认）：Testcontainers 现拉 pgvector + redis
 *  live 通道（-Dlive=true 或 LIVE_TESTS=true）：直连 compose 的 pg/redis，库名 agentforge_it
 * 两条通道暴露同一组属性，断言代码完全复用。
 */
public final class LiveTestSupport {

    // 同时支持 -Dlive=true 与 CI 环境变量，避免 surefire 未透传系统属性时静默退化为 docker 通道
    private static final boolean LIVE =
            Boolean.getBoolean("live") || "true".equalsIgnoreCase(System.getenv("LIVE_TESTS"));

    private static PostgreSQLContainer<?> pg;
    private static GenericContainer<?> redis;

    static {
        if (!LIVE) {
            try {
                pg = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
                        .asCompatibleSubstituteFor("postgres"))
                        .withDatabaseName("agentforge").withUsername("postgres").withPassword("password123");
                pg.start();
                redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                        .withCommand("redis-server", "--requirepass", "redis123").withExposedPorts(6379);
                redis.start();
            } catch (Throwable ignored) {
                // docker 不可用 → 由 @EnabledIf 跳过
            }
        }
    }

    public static boolean usable() {
        return LIVE || (pg != null && pg.isRunning() && redis != null && redis.isRunning());
    }

    public static void register(DynamicPropertyRegistry r) {
        if (LIVE) {
            String host = System.getProperty("live.db.host", "localhost");
            r.add("spring.datasource.url", () -> "jdbc:postgresql://" + host + ":5432/agentforge_it?stringtype=unspecified");
            r.add("spring.datasource.username", () -> "postgres");
            r.add("spring.datasource.password", () -> "password123");
            r.add("spring.data.redis.host", () -> host);
            r.add("spring.data.redis.port", () -> "6379");
            r.add("spring.data.redis.password", () -> "redis123");
            r.add("spring.redis.host", () -> host);
            r.add("spring.redis.port", () -> "6379");
            r.add("spring.redis.password", () -> "redis123");
        } else if (pg != null && pg.isRunning()) {
            r.add("spring.datasource.url", () -> pg.getJdbcUrl() + "&stringtype=unspecified");
            r.add("spring.datasource.username", pg::getUsername);
            r.add("spring.datasource.password", pg::getPassword);
            r.add("spring.data.redis.host", redis::getHost);
            r.add("spring.data.redis.port", redis::getFirstMappedPort);
            r.add("spring.data.redis.password", () -> "redis123");
            r.add("spring.redis.host", redis::getHost);
            r.add("spring.redis.port", redis::getFirstMappedPort);
            r.add("spring.redis.password", () -> "redis123");
        }
    }

    private LiveTestSupport() {}
}

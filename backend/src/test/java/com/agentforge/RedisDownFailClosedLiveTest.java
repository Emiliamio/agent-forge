package com.agentforge;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.security.LuaRateLimiter;
import com.agentforge.security.ReplayGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Redis 宕机降级策略测试 (Fail-Open vs Fail-Closed)")
@EnabledIf("com.agentforge.support.LiveTestSupport#usable")
public class RedisDownFailClosedLiveTest {

    @Autowired
    private ReplayGuard replayGuard;

    @Autowired
    private LuaRateLimiter luaRateLimiter;

    @Test
    @DisplayName("停掉 Redis 后按开关两种取值分别断言 503 / 200 (测完恢复容器)")
    void testRedisDownFailClosedVsFailOpen() {
        try {
            stopRedis();

            // --- 阶段 A: fail-open = false (Fail-Closed 生产策略) ---
            replayGuard.setFailOpenWhenRedisDown(false);
            luaRateLimiter.setFailOpenWhenRedisDown(false);

            // 1. ReplayGuard 拒绝并抛出 503 SERVICE_UNAVAILABLE + Retry-After: 5
            BusinessException exReplay = assertThrows(BusinessException.class, () ->
                    replayGuard.checkAndRecord("test-nonce-fail-closed", (System.currentTimeMillis() / 1000L) + 300)
            );
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), exReplay.getCode());
            assertEquals(5, exReplay.getRetryAfterSeconds());
            assertTrue(exReplay.getMessage().contains("fail-closed 拒绝"));

            // 2. LuaRateLimiter 拒绝并抛出 503 SERVICE_UNAVAILABLE + Retry-After: 5
            BusinessException exLimit = assertThrows(BusinessException.class, () ->
                    luaRateLimiter.tryAcquire("widget:rate:test:fail-closed", 10, 60)
            );
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), exLimit.getCode());
            assertEquals(5, exLimit.getRetryAfterSeconds());
            assertTrue(exLimit.getMessage().contains("fail-closed 拒绝"));

            // --- 阶段 B: fail-open = true (Fail-Open 开发/单机降级策略) ---
            replayGuard.setFailOpenWhenRedisDown(true);
            luaRateLimiter.setFailOpenWhenRedisDown(true);

            // 1. ReplayGuard 平滑降级至本地内存放行 (不抛异常)
            assertDoesNotThrow(() ->
                    replayGuard.checkAndRecord("test-nonce-fail-open", (System.currentTimeMillis() / 1000L) + 300)
            );

            // 2. LuaRateLimiter 平滑降级至本地内存滑动计数放行 (允许请求)
            LuaRateLimiter.RateLimitResult res = luaRateLimiter.tryAcquire("widget:rate:test:fail-open", 10, 60);
            assertNotNull(res);
            assertTrue(res.isAllowed());

        } finally {
            // 恢复 Redis 容器并还原默认配置
            startRedis();
            replayGuard.setFailOpenWhenRedisDown(true);
            luaRateLimiter.setFailOpenWhenRedisDown(true);
        }
    }

    /** 容器名可覆盖（CI 用 docker run 起的容器名与本地 compose 一致，均为 agentforge-redis）。 */
    private static String redisContainer() {
        String fromEnv = System.getenv("REDIS_CONTAINER_NAME");
        return (fromEnv == null || fromEnv.isBlank()) ? "agentforge-redis" : fromEnv;
    }

    private void stopRedis() {
        try {
            int code = new ProcessBuilder("docker", "stop", redisContainer()).inheritIO().start().waitFor();
            if (code != 0) {
                // 不能静默忽略：容器没停下来会让后续断言失真（曾导致 CI 上"应当抛异常却没有"）
                throw new IllegalStateException(
                        "docker stop " + redisContainer() + " 退出码 " + code
                                + "，无法模拟 Redis 宕机；请检查容器名或用 REDIS_CONTAINER_NAME 覆盖");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to stop redis container", e);
        }
    }

    private void startRedis() {
        try {
            new ProcessBuilder("docker", "start", redisContainer()).inheritIO().start().waitFor();
            for (int i = 0; i < 20; i++) {
                int code = new ProcessBuilder("docker", "exec", redisContainer(), "redis-cli", "-a", "redis123", "ping")
                        .start().waitFor();
                if (code == 0) {
                    break;
                }
                Thread.sleep(500);
            }
        } catch (Exception ignored) {
        }
    }
}

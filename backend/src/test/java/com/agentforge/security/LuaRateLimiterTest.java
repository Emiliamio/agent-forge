package com.agentforge.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LuaRateLimiter 原子多维限流器与降级测试")
public class LuaRateLimiterTest {

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    private LuaRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new LuaRateLimiter(null);
    }

    @Test
    @DisplayName("1. 单 key 首次递增并正确获取 TTL 与放行状态")
    void testSingleKeyIncrementAndTtl() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("60")))
                .thenReturn(Arrays.asList(1L, 60L));

        LuaRateLimiter redisLimiter = new LuaRateLimiter(redisTemplateProvider);
        LuaRateLimiter.RateLimitResult res = redisLimiter.tryAcquire("test:key:1", 30, 60);

        assertTrue(res.isAllowed());
        assertEquals(1, res.getCurrent());
        assertEquals(30, res.getLimit());
        assertEquals(60, res.getTtlSeconds());
        assertEquals(29, res.getRemaining());
    }

    @Test
    @DisplayName("2. 超限阻断：current > limit 返回 allowed=false 与 remaining=0")
    void testRateLimitExceeded() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("60")))
                .thenReturn(Arrays.asList(31L, 45L));

        LuaRateLimiter redisLimiter = new LuaRateLimiter(redisTemplateProvider);
        LuaRateLimiter.RateLimitResult res = redisLimiter.tryAcquire("test:key:exceeded", 30, 60);

        assertFalse(res.isAllowed());
        assertEquals(31, res.getCurrent());
        assertEquals(30, res.getLimit());
        assertEquals(45, res.getTtlSeconds());
        assertEquals(0, res.getRemaining());
    }

    @Test
    @DisplayName("3. Redis 宕机异常平滑降级至本地内存滑动窗口")
    void testFallbackToMemorySlidingWindow() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
                .thenThrow(new RuntimeException("Redis connection pool exhausted"));

        LuaRateLimiter fallbackLimiter = new LuaRateLimiter(redisTemplateProvider);
        String key = "test:fallback:" + UUID.randomUUID();

        // 限制 3 次
        for (int i = 1; i <= 3; i++) {
            LuaRateLimiter.RateLimitResult r = fallbackLimiter.tryAcquire(key, 3, 60);
            assertTrue(r.isAllowed(), "第 " + i + " 次应允许放行");
            assertEquals(i, r.getCurrent());
            assertEquals(3 - i, r.getRemaining());
        }

        // 第 4 次与第 5 次应被阻断
        LuaRateLimiter.RateLimitResult r4 = fallbackLimiter.tryAcquire(key, 3, 60);
        assertFalse(r4.isAllowed(), "第 4 次应被限流阻断");
        assertEquals(4, r4.getCurrent());
        assertEquals(0, r4.getRemaining());

        LuaRateLimiter.RateLimitResult r5 = fallbackLimiter.tryAcquire(key, 3, 60);
        assertFalse(r5.isAllowed(), "第 5 次应被限流阻断");
        assertEquals(5, r5.getCurrent());
    }

    @Test
    @DisplayName("4. 本地内存高并发竞争模拟：40 线程竞争 limit=30")
    void testLocalMemoryConcurrency() throws InterruptedException {
        LuaRateLimiter localLimiter = new LuaRateLimiter(null);
        String key = "test:concurrent:" + UUID.randomUUID();

        int totalThreads = 40;
        int limit = 30;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    LuaRateLimiter.RateLimitResult res = localLimiter.tryAcquire(key, limit, 60);
                    if (res.isAllowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(30, allowedCount.get(), "并发 40 请求应当恰好放行 30 个");
        assertEquals(10, rejectedCount.get(), "并发 40 请求应当恰好拒绝 10 个");
    }
}

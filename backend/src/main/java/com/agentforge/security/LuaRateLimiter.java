package com.agentforge.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于原子 Redis Lua 脚本的高并发多维限流器
 * 保证 INCR、EXPIRE 与 TTL 探针在单次原子指令中完成，规避进程 Crash 导致的无 TTL 永久锁；
 * 当 Redis 宕机或异常时，平滑降级至本地内存滑动时间窗口，保障业务零中断与真 Retry-After 计算。
 */
@Slf4j
@Component
public class LuaRateLimiter {

    private static final int MAX_LRU_ENTRIES = 10000;

    private static final String LUA_SCRIPT =
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if current == 1 then\n" +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "local ttl = redis.call('TTL', KEYS[1])\n" +
            "return {current, ttl}";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final RedisScript<List> rateLimitScript;

    @org.springframework.beans.factory.annotation.Value("${agentforge.security.fail-open-when-redis-down:true}")
    private boolean failOpenWhenRedisDown = true;

    public void setFailOpenWhenRedisDown(boolean failOpenWhenRedisDown) {
        this.failOpenWhenRedisDown = failOpenWhenRedisDown;
    }

    private final Map<String, MemoryRateBucket> memoryLru = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, MemoryRateBucket> eldest) {
            return size() > MAX_LRU_ENTRIES;
        }
    };

    public LuaRateLimiter(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(LUA_SCRIPT);
        script.setResultType(List.class);
        this.rateLimitScript = script;
    }

    /**
     * 尝试获取执行许可
     *
     * @param key           限流键 (如 widget:rate:ip:127.0.0.1)
     * @param limit         单位时间最大请求数
     * @param windowSeconds 时间窗口 (秒)
     * @return 限流结果对象
     */
    public RateLimitResult tryAcquire(String key, long limit, long windowSeconds) {
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;

        if (redis != null) {
            try {
                @SuppressWarnings("unchecked")
                List<Object> result = redis.execute(
                        rateLimitScript,
                        Collections.singletonList(key),
                        String.valueOf(windowSeconds)
                );

                if (result != null && result.size() >= 2) {
                    long current = ((Number) result.get(0)).longValue();
                    long ttl = ((Number) result.get(1)).longValue();
                    if (ttl <= 0) {
                        ttl = windowSeconds;
                    }
                    boolean allowed = current <= limit;
                    return new RateLimitResult(current, limit, ttl, allowed);
                }
            } catch (Exception e) {
                log.warn("LuaRateLimiter Redis 执行异常: key={}, error={}", key, e.getMessage());
                if (!failOpenWhenRedisDown) {
                    throw new com.agentforge.exception.BusinessException(
                            com.agentforge.exception.ErrorCode.SERVICE_UNAVAILABLE,
                            "限流/防重放组件不可用，已按 fail-closed 拒绝",
                            5
                    );
                }
            }
        } else {
            if (!failOpenWhenRedisDown) {
                throw new com.agentforge.exception.BusinessException(
                        com.agentforge.exception.ErrorCode.SERVICE_UNAVAILABLE,
                        "限流/防重放组件不可用，已按 fail-closed 拒绝",
                        5
                );
            }
        }

        return tryAcquireInMemory(key, limit, windowSeconds);
    }

    private synchronized RateLimitResult tryAcquireInMemory(String key, long limit, long windowSeconds) {
        long now = System.currentTimeMillis();
        MemoryRateBucket bucket = memoryLru.get(key);

        if (bucket == null || now >= bucket.expireAtMs) {
            bucket = new MemoryRateBucket(new AtomicLong(1), now + (windowSeconds * 1000L), windowSeconds);
            memoryLru.put(key, bucket);
            return new RateLimitResult(1, limit, windowSeconds, 1 <= limit);
        }

        long current = bucket.counter.incrementAndGet();
        long ttlSeconds = Math.max(1, (bucket.expireAtMs - now) / 1000L);
        boolean allowed = current <= limit;
        return new RateLimitResult(current, limit, ttlSeconds, allowed);
    }

    public synchronized void clearMemoryCache() {
        memoryLru.clear();
    }

    @Getter
    @ToString
    @AllArgsConstructor
    public static class RateLimitResult {
        private final long current;
        private final long limit;
        private final long ttlSeconds;
        private final boolean allowed;

        public long getRemaining() {
            return Math.max(0, limit - current);
        }
    }

    private static class MemoryRateBucket {
        final AtomicLong counter;
        final long expireAtMs;
        final long windowSeconds;

        MemoryRateBucket(AtomicLong counter, long expireAtMs, long windowSeconds) {
            this.counter = counter;
            this.expireAtMs = expireAtMs;
            this.windowSeconds = windowSeconds;
        }
    }
}

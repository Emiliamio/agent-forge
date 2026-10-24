package com.agentforge.security;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 挂件令牌重放攻击防护器
 * 优先采用 Redis 保证分布式防重放，当 Redis 不可用时自动降级为本地内存 LRU 缓存，保证服务高可用
 */
@Slf4j
@Component
public class ReplayGuard {

    private static final int MAX_LRU_CAPACITY = 10000;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @org.springframework.beans.factory.annotation.Value("${agentforge.security.fail-open-when-redis-down:true}")
    private boolean failOpenWhenRedisDown = true;

    public void setFailOpenWhenRedisDown(boolean failOpenWhenRedisDown) {
        this.failOpenWhenRedisDown = failOpenWhenRedisDown;
    }

    private final Map<String, Long> memoryLru = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_LRU_CAPACITY;
        }
    };

    public ReplayGuard(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * 校验并记录 Nonce
     *
     * @param nonce 随机串
     * @param exp   令牌过期秒级时间戳
     * @throws BusinessException 当检测到重放时抛出 401 UNAUTHORIZED
     */
    public void checkAndRecord(String nonce, long exp) {
        if (nonce == null || nonce.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证 (Nonce) 缺失");
        }

        long nowSeconds = System.currentTimeMillis() / 1000L;
        long ttlSeconds = Math.max(exp - nowSeconds + 300, 60);

        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        boolean recordedInRedis = false;

        if (redis != null) {
            try {
                String key = "widget:nonce:" + nonce;
                Boolean isNew = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds));
                if (Boolean.FALSE.equals(isNew)) {
                    markAuditOutcome("replay_blocked");
                    log.warn("🛑 [REPLAY_ATTACK_DETECTED] 挂件 Nonce 重放拦截 (Redis): nonce={}", nonce);
                    throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证已被使用 (重放拦截)");
                }
                recordedInRedis = true;
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                log.warn("ReplayGuard Redis 异常: {}", e.getMessage());
                if (!failOpenWhenRedisDown) {
                    throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "限流/防重放组件不可用，已按 fail-closed 拒绝", 5);
                }
            }
        } else {
            if (!failOpenWhenRedisDown) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "限流/防重放组件不可用，已按 fail-closed 拒绝", 5);
            }
        }

        if (!recordedInRedis) {
            checkAndRecordInMemory(nonce, exp);
        }
    }

    private synchronized void checkAndRecordInMemory(String nonce, long exp) {
        long nowMs = System.currentTimeMillis();
        Long existingExpMs = memoryLru.get(nonce);
        if (existingExpMs != null && existingExpMs > nowMs) {
            markAuditOutcome("replay_blocked");
            log.warn("🛑 [REPLAY_ATTACK_DETECTED] 挂件 Nonce 重放拦截 (Local LRU): nonce={}", nonce);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证已被使用 (重放拦截)");
        }
        memoryLru.put(nonce, (exp + 300) * 1000L);
    }

    private void markAuditOutcome(String outcome) {
        try {
            org.springframework.web.context.request.RequestAttributes attrs =
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
                sra.getRequest().setAttribute("widget_audit_outcome", outcome);
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void clearMemoryCache() {
        memoryLru.clear();
    }
}

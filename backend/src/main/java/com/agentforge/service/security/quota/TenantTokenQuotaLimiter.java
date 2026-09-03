package com.agentforge.service.security.quota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多租户动态 Token 消费预算与并发限流熔断器 (Tenant Token Quota & Rate Limiter)
 * 对标 OpenAI Tier-Quota 与商业 SaaS 成本熔断标准：
 * 1. 严格按照租户 ID 实施单日 Token 预算硬隔离；
 * 2. 基于滑动窗口限制每分钟并发请求数 (RPM)；
 * 3. 一旦超出预算或限流，返回标准 429 决策与 Retry-After 秒数，防止算力被恶意刷爆。
 */
@Service
public class TenantTokenQuotaLimiter {

    private static final Logger log = LoggerFactory.getLogger(TenantTokenQuotaLimiter.class);

    public static class QuotaPolicy implements Serializable {
        private final long dailyTokenBudget;
        private final int maxRpm;

        public QuotaPolicy(long dailyTokenBudget, int maxRpm) {
            this.dailyTokenBudget = dailyTokenBudget;
            this.maxRpm = maxRpm;
        }

        public long getDailyTokenBudget() { return dailyTokenBudget; }
        public int getMaxRpm() { return maxRpm; }
    }

    public static class QuotaDecision implements Serializable {
        private final boolean allowed;
        private final long remainingTokens;
        private final int currentRpm;
        private final int retryAfterSeconds;
        private final String rejectionReason;

        public QuotaDecision(boolean allowed, long remainingTokens, int currentRpm, int retryAfterSeconds, String rejectionReason) {
            this.allowed = allowed;
            this.remainingTokens = remainingTokens;
            this.currentRpm = currentRpm;
            this.retryAfterSeconds = retryAfterSeconds;
            this.rejectionReason = rejectionReason;
        }

        public boolean isAllowed() { return allowed; }
        public long getRemainingTokens() { return remainingTokens; }
        public int getCurrentRpm() { return currentRpm; }
        public int getRetryAfterSeconds() { return retryAfterSeconds; }
        public String getRejectionReason() { return rejectionReason; }
    }

    // 租户策略配置
    private final Map<Long, QuotaPolicy> policyMap = new ConcurrentHashMap<>();

    // 租户单日已消耗 Token 计数器
    private final Map<Long, AtomicLong> dailyUsageMap = new ConcurrentHashMap<>();

    // 租户当前分钟并发计数器
    private final Map<Long, AtomicInteger> rpmCounterMap = new ConcurrentHashMap<>();
    private final Map<Long, Long> rpmWindowStartMap = new ConcurrentHashMap<>();

    private static final QuotaPolicy DEFAULT_POLICY = new QuotaPolicy(100_000L, 60);

    /**
     * 为租户配置专属配额策略
     */
    public void configurePolicy(Long tenantId, long dailyTokenBudget, int maxRpm) {
        policyMap.put(tenantId, new QuotaPolicy(dailyTokenBudget, maxRpm));
        log.info("⚙️ [QUOTA_CONFIG] 已更新租户配额策略: tenantId={}, budget={}, maxRpm={}",
                tenantId, dailyTokenBudget, maxRpm);
    }

    /**
     * 检查并尝试锁定/预扣 Token 配额
     */
    public QuotaDecision checkAndAcquire(Long tenantId, int estimatedTokens) {
        if (tenantId == null) tenantId = 1L;

        QuotaPolicy policy = policyMap.getOrDefault(tenantId, DEFAULT_POLICY);
        long now = System.currentTimeMillis();

        // 1. RPM 速率窗口检查 (滑动 60 秒)
        rpmWindowStartMap.putIfAbsent(tenantId, now);
        long windowStart = rpmWindowStartMap.get(tenantId);
        AtomicInteger rpmCounter = rpmCounterMap.computeIfAbsent(tenantId, k -> new AtomicInteger(0));

        if (now - windowStart > 60_000) {
            rpmCounter.set(0);
            rpmWindowStartMap.put(tenantId, now);
        }

        if (rpmCounter.get() >= policy.getMaxRpm()) {
            int retryAfter = (int) Math.max(1, (60_000 - (now - windowStart)) / 1000);
            log.warn("🛑 [QUOTA_REJECTED] 租户并发请求超限 (RPM 熔断): tenantId={}, currentRpm={}",
                    tenantId, rpmCounter.get());
            return new QuotaDecision(false, 0, rpmCounter.get(), retryAfter, "Tenant RPM limit exceeded");
        }

        // 2. 单日总 Token 预算检查
        AtomicLong usage = dailyUsageMap.computeIfAbsent(tenantId, k -> new AtomicLong(0));
        long currentUsage = usage.get();
        if (currentUsage + estimatedTokens > policy.getDailyTokenBudget()) {
            long remaining = Math.max(0, policy.getDailyTokenBudget() - currentUsage);
            log.warn("🛑 [QUOTA_REJECTED] 租户单日 Token 预算耗尽: tenantId={}, used={}, budget={}",
                    tenantId, currentUsage, policy.getDailyTokenBudget());
            return new QuotaDecision(false, remaining, rpmCounter.get(), 3600, "Daily token quota exhausted");
        }

        // 准入通过：原子自增
        rpmCounter.incrementAndGet();
        long newUsage = usage.addAndGet(estimatedTokens);
        long remaining = Math.max(0, policy.getDailyTokenBudget() - newUsage);

        return new QuotaDecision(true, remaining, rpmCounter.get(), 0, null);
    }

    /**
     * 重置租户单日用量（用于测试或次日零点定时刷新）
     */
    public void resetDailyUsage(Long tenantId) {
        if (tenantId != null) {
            dailyUsageMap.remove(tenantId);
            rpmCounterMap.remove(tenantId);
        }
    }
}
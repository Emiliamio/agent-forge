package com.agentforge.service.agent.llm.pool;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工业级大模型 API Key 智能负载轮询与故障/欠费自动摘除池 (API Key Pool Manager)
 * 解决买家单个 Key 突然欠费导致全站问答瘫痪并投诉的痛点
 */
@Slf4j
@Component
public class ApiKeyPoolManager {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyStatus implements Serializable {
        private static final long serialVersionUID = 1L;

        private String apiKey;
        private boolean isAvailable;
        private int failureCount;
        private String lastError;
    }

    private final List<KeyStatus> keyPool = new CopyOnWriteArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    public ApiKeyPoolManager() {
        // 初始化默认测试密钥
        addKey("sk-default-agentforge-master-key");
    }

    /**
     * 动态批量添加密钥 (支持逗号分隔)
     */
    public synchronized void addKey(String rawKeys) {
        if (StrUtil.isBlank(rawKeys)) return;
        String[] keys = rawKeys.split(",");
        for (String k : keys) {
            String trimmed = k.trim();
            if (!trimmed.isEmpty()) {
                boolean exists = keyPool.stream().anyMatch(item -> item.getApiKey().equals(trimmed));
                if (!exists) {
                    keyPool.add(KeyStatus.builder()
                            .apiKey(trimmed)
                            .isAvailable(true)
                            .failureCount(0)
                            .build());
                }
            }
        }
        log.info("🔑 API Key 负载池更新: 当前可用密钥数={}", getAvailableCount());
    }

    /**
     * 获取下一个可用的健康 API Key (Round-Robin 负载均衡)
     */
    public String getNextHealthyKey() {
        List<KeyStatus> available = keyPool.stream().filter(KeyStatus::isAvailable).toList();
        if (available.isEmpty()) {
            log.warn("⚠️ API Key 负载池所有密钥已耗尽或已熔断，使用默认保底 Key");
            return "sk-default-fallback-key";
        }
        int idx = Math.abs(roundRobinIndex.getAndIncrement() % available.size());
        return available.get(idx).getApiKey();
    }

    /**
     * 标记指定 Key 异常 (遇到 401 密钥失效或 429 欠费限流时自动摘除)
     */
    public void markKeyFailed(String key, String errorReason) {
        for (KeyStatus ks : keyPool) {
            if (ks.getApiKey().equals(key)) {
                ks.setFailureCount(ks.getFailureCount() + 1);
                ks.setLastError(errorReason);
                if (ks.getFailureCount() >= 2) {
                    ks.setAvailable(false);
                    log.error("🚨 API Key 发生故障/欠费，已自动从负载池摘除: key={}..., error={}",
                            StrUtil.maxLength(key, 8), errorReason);
                }
                break;
            }
        }
    }

    public int getAvailableCount() {
        return (int) keyPool.stream().filter(KeyStatus::isAvailable).count();
    }

    public List<KeyStatus> listAllKeys() {
        return new ArrayList<>(keyPool);
    }
}

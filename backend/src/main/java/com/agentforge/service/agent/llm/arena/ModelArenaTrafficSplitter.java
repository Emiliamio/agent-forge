package com.agentforge.service.agent.llm.arena;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多模型金丝雀灰度分流与竞技场评测器 (Model Arena Traffic Splitter & Benchmark)
 * 对标 LMSYS Chatbot Arena 与 Portkey 企业级多模型评估标准：
 * 1. 支持配置多模型加权金丝雀灰度路由 (如 80% DeepSeek-V3, 20% DeepSeek-R1)；
 * 2. 支持基于租户/用户 ID 进行 Deterministic Hash 会话一致性路由；
 * 3. 自动沉淀各模型的首字时延 (TTFT)、推理吞吐与用户满意度评测 (Elo/胜率看板)。
 */
@Service
public class ModelArenaTrafficSplitter {

    private static final Logger log = LoggerFactory.getLogger(ModelArenaTrafficSplitter.class);

    public static class ModelCandidate implements Serializable {
        private final String modelId;
        private final int weight; // 0 ~ 100

        public ModelCandidate(String modelId, int weight) {
            this.modelId = modelId;
            this.weight = weight;
        }

        public String getModelId() { return modelId; }
        public int getWeight() { return weight; }
    }

    public static class ModelMetrics implements Serializable {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong totalLatencyMs = new AtomicLong(0);
        private final AtomicInteger thumbsUp = new AtomicInteger(0);
        private final AtomicInteger thumbsDown = new AtomicInteger(0);

        public void recordRequest(long latencyMs) {
            totalRequests.incrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
        }

        public void recordFeedback(boolean positive) {
            if (positive) thumbsUp.incrementAndGet();
            else thumbsDown.incrementAndGet();
        }

        public long getTotalRequests() { return totalRequests.get(); }
        public double getAvgLatencyMs() {
            long reqs = totalRequests.get();
            return reqs > 0 ? (double) totalLatencyMs.get() / reqs : 0.0;
        }
        public int getThumbsUp() { return thumbsUp.get(); }
        public int getThumbsDown() { return thumbsDown.get(); }
    }

    private final List<ModelCandidate> candidateList = new ArrayList<>();
    private final Map<String, ModelMetrics> metricsMap = new ConcurrentHashMap<>();

    public synchronized void setCandidates(List<ModelCandidate> candidates) {
        this.candidateList.clear();
        if (candidates != null) {
            this.candidateList.addAll(candidates);
            for (ModelCandidate c : candidates) {
                metricsMap.putIfAbsent(c.getModelId(), new ModelMetrics());
            }
        }
        log.info("⚖️ [MODEL_ARENA_CONFIG] 模型竞技场候选已更新，总数: {}", candidateList.size());
    }

    /**
     * 基于租户或用户 ID 进行确定性一致性 Hash 路由
     */
    public String routeConsistent(String sessionOrUserId) {
        if (candidateList.isEmpty()) {
            return "default-model";
        }
        int totalWeight = candidateList.stream().mapToInt(ModelCandidate::getWeight).sum();
        if (totalWeight <= 0) {
            return candidateList.get(0).getModelId();
        }

        int hash = Math.abs(sessionOrUserId != null ? sessionOrUserId.hashCode() : 0);
        int targetSlot = hash % totalWeight;

        int accumulated = 0;
        for (ModelCandidate candidate : candidateList) {
            accumulated += candidate.getWeight();
            if (targetSlot < accumulated) {
                return candidate.getModelId();
            }
        }
        return candidateList.get(0).getModelId();
    }

    /**
     * 记录模型请求性能与时延
     */
    public void recordMetric(String modelId, long latencyMs) {
        ModelMetrics m = metricsMap.computeIfAbsent(modelId, k -> new ModelMetrics());
        m.recordRequest(latencyMs);
    }

    /**
     * 记录用户对生成结果的满意度点赞/点踩
     */
    public void recordUserFeedback(String modelId, boolean thumbsUp) {
        ModelMetrics m = metricsMap.computeIfAbsent(modelId, k -> new ModelMetrics());
        m.recordFeedback(thumbsUp);
        log.info("👍 [USER_FEEDBACK] 模型 {} 收到用户反馈: positive={}", modelId, thumbsUp);
    }

    public ModelMetrics getMetrics(String modelId) {
        return metricsMap.get(modelId);
    }
}
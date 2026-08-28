package com.agentforge.service.billing;

/**
 * 商业化 Token 计量与计费中心服务接口
 */
public interface BillingService {

    /**
     * 记录 Token 消耗并执行金融级原子扣费 (支持语义缓存 0 成本结算与欠费熔断)
     *
     * @param appId            应用 ID
     * @param userId           用户 ID
     * @param apiKeyId         API Key ID
     * @param modelName        物理调用的模型名称
     * @param promptTokens     输入 Token 数
     * @param completionTokens 输出 Token 数
     * @param isCached         是否命中语义缓存
     * @param durationMs       调用耗时 (毫秒)
     */
    void recordAndDeduct(
            Long appId,
            Long userId,
            Long apiKeyId,
            String modelName,
            int promptTokens,
            int completionTokens,
            boolean isCached,
            long durationMs
    );
}

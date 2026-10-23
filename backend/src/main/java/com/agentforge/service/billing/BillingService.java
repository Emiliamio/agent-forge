package com.agentforge.service.billing;

import com.agentforge.entity.TokenUsageLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 商业化 Token 计量与计费中心服务接口
 */
public interface BillingService {

    /**
     * 记录 Token 消耗并执行金融级原子扣费 (支持语义缓存 0 成本结算与欠费熔断)
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

    /**
     * 获取指定租户的 Token 消耗与计费统计看板数据
     */
    Map<String, Object> getBillingStats(Long tenantId);

    /**
     * 分页查询指定租户的 Token 消耗审计日志
     */
    Page<TokenUsageLog> listUsageLogs(Long tenantId, long pageNum, long pageSize);

    com.agentforge.vo.PageResult<com.agentforge.vo.TokenUsageLogVo> listUsageLogsVo(Long tenantId, long pageNum, long pageSize);

    /**
     * 租户钱包在线充值
     */
    BigDecimal recharge(Long tenantId, BigDecimal amount);
}

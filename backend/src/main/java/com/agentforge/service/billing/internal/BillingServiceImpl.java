package com.agentforge.service.billing.internal;

import com.agentforge.service.billing.api.BillingService;

import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.SysTenant;
import com.agentforge.entity.TokenUsageLog;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.SysTenantMapper;
import com.agentforge.mapper.TokenUsageLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 商业化 Token 计量与计费中心实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final SysTenantMapper tenantMapper;
    private final TokenUsageLogMapper tokenUsageLogMapper;

    // 模型定价表：每 1000 Tokens 单价(元)
    private static final BigDecimal DEEPSEEK_INPUT_PRICE = new BigDecimal("0.001");
    private static final BigDecimal DEEPSEEK_OUTPUT_PRICE = new BigDecimal("0.002");
    private static final BigDecimal OPENAI_INPUT_PRICE = new BigDecimal("0.015");
    private static final BigDecimal OPENAI_OUTPUT_PRICE = new BigDecimal("0.060");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordAndDeduct(
            Long appId,
            Long userId,
            Long apiKeyId,
            String modelName,
            int promptTokens,
            int completionTokens,
            boolean isCached,
            long durationMs
    ) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }

        // 1. 计算本次调用的消费金额 (命中语义缓存则直接 0 成本)
        BigDecimal costAmount = BigDecimal.ZERO;
        if (!isCached) {
            BigDecimal inRate = (modelName != null && modelName.contains("gpt")) ? OPENAI_INPUT_PRICE : DEEPSEEK_INPUT_PRICE;
            BigDecimal outRate = (modelName != null && modelName.contains("gpt")) ? OPENAI_OUTPUT_PRICE : DEEPSEEK_OUTPUT_PRICE;

            BigDecimal inCost = inRate.multiply(BigDecimal.valueOf(promptTokens)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            BigDecimal outCost = outRate.multiply(BigDecimal.valueOf(completionTokens)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            costAmount = inCost.add(outCost);
        }

        // 2. 校验余额并扣费 (如果钱包透支且设置了熔断阻断)
        if (tenant.getWalletBalance().compareTo(costAmount) < 0 && costAmount.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("租户额度不足触发熔断: tenantId={}, balance={}, required={}", tenantId, tenant.getWalletBalance(), costAmount);
            throw new BusinessException(ErrorCode.TENANT_WALLET_EXHAUSTED);
        }

        tenant.setWalletBalance(tenant.getWalletBalance().subtract(costAmount));
        tenantMapper.updateById(tenant);

        // 3. 异步审计日志落盘
        TokenUsageLog usageLog = TokenUsageLog.builder()
                .tenantId(tenantId)
                .appId(appId != null ? appId : 0L)
                .userId(userId != null ? userId : 0L)
                .apiKeyId(apiKeyId != null ? apiKeyId : 0L)
                .modelName(modelName != null ? modelName : "deepseek-chat")
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .isCached(isCached)
                .costAmount(costAmount)
                .durationMs(durationMs)
                .createdAt(LocalDateTime.now())
                .build();

        tokenUsageLogMapper.insert(usageLog);
    }

    @Override
    public Map<String, Object> getBillingStats(Long tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }

        Long totalCalls = tokenUsageLogMapper.selectCount(new LambdaQueryWrapper<TokenUsageLog>().eq(TokenUsageLog::getTenantId, tenantId));
        Long cachedCalls = tokenUsageLogMapper.selectCount(new LambdaQueryWrapper<TokenUsageLog>().eq(TokenUsageLog::getTenantId, tenantId).eq(TokenUsageLog::getIsCached, true));

        double cacheHitRate = totalCalls > 0 ? (double) cachedCalls / totalCalls : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("tenantName", tenant.getName());
        stats.put("walletBalance", tenant.getWalletBalance());
        stats.put("totalCalls", totalCalls);
        stats.put("cachedCalls", cachedCalls);
        stats.put("cacheHitRate", String.format("%.1f%%", cacheHitRate * 100));
        stats.put("estimatedCostSaved", String.format("¥ %.2f", cachedCalls * 0.005));

        return stats;
    }

    @Override
    public com.agentforge.vo.PageResult<com.agentforge.vo.TokenUsageLogVo> listUsageLogsVo(Long tenantId, long pageNum, long pageSize) {
        Page<TokenUsageLog> page = listUsageLogs(tenantId, pageNum, pageSize);
        java.util.List<com.agentforge.vo.TokenUsageLogVo> list = page.getRecords().stream()
                .map(com.agentforge.service.converter.TokenUsageLogConverter::toVo)
                .toList();
        return com.agentforge.vo.PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), list);
    }

    @Override
    public Page<TokenUsageLog> listUsageLogs(Long tenantId, long pageNum, long pageSize) {
        return tokenUsageLogMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<TokenUsageLog>().eq(TokenUsageLog::getTenantId, tenantId).orderByDesc(TokenUsageLog::getId)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal recharge(Long tenantId, BigDecimal amount) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "充值金额必须大于 0");
        }

        tenant.setWalletBalance(tenant.getWalletBalance().add(amount));
        tenantMapper.updateById(tenant);

        return tenant.getWalletBalance();
    }
}

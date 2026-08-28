package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.SysTenant;
import com.agentforge.entity.TokenUsageLog;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.SysTenantMapper;
import com.agentforge.mapper.TokenUsageLogMapper;
import com.agentforge.vo.PageResult;
import com.agentforge.vo.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 商业化计量、Token 消耗统计与租户钱包充值接口
 */
@Tag(name = "08. 商业化计量与计费看板", description = "提供租户钱包余额、Token 消耗流水与在线充值")
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
@SaCheckLogin
public class BillingController {

    private final SysTenantMapper tenantMapper;
    private final TokenUsageLogMapper tokenUsageLogMapper;

    @Operation(summary = "获取当前租户 Token 消耗与计费统计看板")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getBillingStats() {
        Long tenantId = TenantContextHolder.getTenantId();
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

        return Result.success(stats);
    }

    @Operation(summary = "分页查询 Token 消耗审计日志")
    @GetMapping("/logs")
    public Result<PageResult<TokenUsageLog>> listUsageLogs(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        Long tenantId = TenantContextHolder.getTenantId();
        Page<TokenUsageLog> page = tokenUsageLogMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<TokenUsageLog>().eq(TokenUsageLog::getTenantId, tenantId).orderByDesc(TokenUsageLog::getId)
        );
        return Result.success(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "租户钱包在线充值")
    @PostMapping("/recharge")
    public Result<BigDecimal> recharge(@RequestBody RechargeRequest request) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "充值金额必须大于 0");
        }

        tenant.setWalletBalance(tenant.getWalletBalance().add(request.getAmount()));
        tenantMapper.updateById(tenant);

        return Result.success("充值成功", tenant.getWalletBalance());
    }

    @Data
    public static class RechargeRequest {
        private BigDecimal amount;
    }
}

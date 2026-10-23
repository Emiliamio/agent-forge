package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.dto.RechargeRequest;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.service.billing.BillingService;
import com.agentforge.vo.PageResult;
import com.agentforge.vo.Result;
import com.agentforge.vo.TokenUsageLogVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
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

    private final BillingService billingService;

    @Operation(summary = "获取当前租户 Token 消耗与计费统计看板")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getBillingStats() {
        Long tenantId = TenantContextHolder.getTenantId();
        return Result.success(billingService.getBillingStats(tenantId));
    }

    @Operation(summary = "按租户 ID 查询用量详情 (跨租户越权防护)")
    @GetMapping("/tenant/{tenantId}/usage")
    public Result<Map<String, Object>> getTenantUsage(@PathVariable("tenantId") Long targetTenantId) {
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (currentTenantId == null || !currentTenantId.equals(targetTenantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "跨租户安全拦截：严禁越权访问其他租户资产");
        }
        return getBillingStats();
    }

    @Operation(summary = "分页查询 Token 消耗审计日志")
    @GetMapping("/logs")
    public Result<PageResult<TokenUsageLogVo>> listUsageLogs(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        Long tenantId = TenantContextHolder.getTenantId();
        return Result.success(billingService.listUsageLogsVo(tenantId, pageNum, pageSize));
    }

    @Operation(summary = "租户钱包在线充值")
    @PostMapping("/recharge")
    public Result<BigDecimal> recharge(@RequestBody RechargeRequest request) {
        Long tenantId = TenantContextHolder.getTenantId();
        BigDecimal balance = billingService.recharge(tenantId, request.getAmount());
        return Result.success("充值成功", balance);
    }
}

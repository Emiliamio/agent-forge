package com.agentforge.context;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 租户上下文管理器 (基于 ThreadLocal)
 * 实现当前请求/执行线程的租户 ID 透传与隔离
 */
@Slf4j
public class TenantContextHolder {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    /**
     * 默认演示租户 ID
     */
    public static final Long DEFAULT_TENANT_ID = 1L;

    /**
     * 设置当前线程租户 ID
     */
    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * 获取当前线程租户 ID (若未设置则安全降级为默认租户)
     */
    public static Long getTenantId() {
        Long tenantId = CURRENT_TENANT.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }

    /**
     * 清理当前线程租户上下文 (防内存泄露)
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * 在指定租户上下文中执行代码块 (常用于异步任务、工作流调度)
     */
    public static <T> T runWithTenant(Long tenantId, Supplier<T> supplier) {
        Long previousTenant = CURRENT_TENANT.get();
        try {
            setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (previousTenant != null) {
                setTenantId(previousTenant);
            } else {
                clear();
            }
        }
    }

    /**
     * 在指定租户上下文中执行 Runnable
     */
    public static void runWithTenant(Long tenantId, Runnable runnable) {
        runWithTenant(tenantId, () -> {
            runnable.run();
            return null;
        });
    }
}

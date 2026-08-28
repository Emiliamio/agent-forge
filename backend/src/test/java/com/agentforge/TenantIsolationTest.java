package com.agentforge;

import com.agentforge.context.TenantContextHolder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@DisplayName("多租户上下文透传与隔离单元测试")
public class TenantIsolationTest {

    @Test
    @DisplayName("测试单线程租户上下文设置与安全清理")
    void testTenantContextLifecycle() {
        try {
            TenantContextHolder.setTenantId(888L);
            Assertions.assertEquals(888L, TenantContextHolder.getTenantId());
        } finally {
            TenantContextHolder.clear();
        }
        // 清理后降级为系统默认租户 1
        Assertions.assertEquals(TenantContextHolder.DEFAULT_TENANT_ID, TenantContextHolder.getTenantId());
    }

    @Test
    @DisplayName("测试 runWithTenant 作用域执行与自动复原")
    void testRunWithTenantScope() {
        TenantContextHolder.setTenantId(100L);

        String result = TenantContextHolder.runWithTenant(200L, () -> {
            Assertions.assertEquals(200L, TenantContextHolder.getTenantId());
            return "SUCCESS_200";
        });

        Assertions.assertEquals("SUCCESS_200", result);
        // 执行完毕后还原为外层租户 100
        Assertions.assertEquals(100L, TenantContextHolder.getTenantId());
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("测试多线程环境下的租户上下文物理隔离")
    void testMultiThreadTenantIsolation() throws ExecutionException, InterruptedException {
        TenantContextHolder.setTenantId(1001L);

        CompletableFuture<Long> futureTenant2 = CompletableFuture.supplyAsync(() -> {
            TenantContextHolder.setTenantId(2002L);
            return TenantContextHolder.getTenantId();
        });

        Long thread2TenantId = futureTenant2.get();
        Long mainThreadTenantId = TenantContextHolder.getTenantId();

        // 断言不同线程中的租户 ID 独立，互不污染
        Assertions.assertEquals(1001L, mainThreadTenantId);
        Assertions.assertEquals(2002L, thread2TenantId);

        TenantContextHolder.clear();
    }
}

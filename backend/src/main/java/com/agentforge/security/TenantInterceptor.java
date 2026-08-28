package com.agentforge.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.agentforge.context.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户 HTTP 请求拦截器
 * 从 Header、Query 参数或登录凭证中解析租户 ID 并注入上下文
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String PARAM_TENANT_ID = "tenantId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantHeader = request.getHeader(HEADER_TENANT_ID);
        if (StrUtil.isNotBlank(tenantHeader)) {
            try {
                TenantContextHolder.setTenantId(Long.parseLong(tenantHeader));
                return true;
            } catch (NumberFormatException ignored) {
            }
        }

        // 尝试从 Query 参数获取
        String tenantParam = request.getParameter(PARAM_TENANT_ID);
        if (StrUtil.isNotBlank(tenantParam)) {
            try {
                TenantContextHolder.setTenantId(Long.parseLong(tenantParam));
                return true;
            } catch (NumberFormatException ignored) {
            }
        }

        // 尝试从 Sa-Token 登录会话中获取
        try {
            if (StpUtil.isLogin()) {
                Object tenantIdObj = StpUtil.getExtra("tenantId");
                if (tenantIdObj != null) {
                    TenantContextHolder.setTenantId(Long.valueOf(tenantIdObj.toString()));
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        // 默认降级为系统默认租户
        TenantContextHolder.setTenantId(TenantContextHolder.DEFAULT_TENANT_ID);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束务必清理 ThreadLocal，防止 Tomcat/虚拟线程池线程复用导致数据串租户
        TenantContextHolder.clear();
    }
}

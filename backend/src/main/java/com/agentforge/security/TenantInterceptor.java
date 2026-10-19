package com.agentforge.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户 HTTP 请求拦截器
 * 严格按照大厂安全规范解析租户 ID：
 * 1. 登录用户强制从 Sa-Token 会话凭据中读取绑定的真实租户 ID，严防修改 X-Tenant-Id 请求头物理越权；
 * 2. 未登录的公开请求（如登录接口、健康检查）仅做上下文兜底或预解析。
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String PARAM_TENANT_ID = "tenantId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 若当前用户已登录，强制从 Sa-Token 登录会话中获取绑定的租户 ID
        try {
            if (StpUtil.isLogin()) {
                Object sessionTenantId = null;
                try {
                    sessionTenantId = StpUtil.getSession().get("tenantId");
                } catch (Exception e) {
                    try {
                        sessionTenantId = StpUtil.getExtra("tenantId");
                    } catch (Exception ignored) {
                    }
                }
                if (sessionTenantId != null) {
                    Long authTenantId = Long.valueOf(sessionTenantId.toString());

                    // 安全防御：如果请求头也携带了 X-Tenant-Id，严格校验是否与会话租户一致，杜绝物理越权
                    String clientHeader = request.getHeader(HEADER_TENANT_ID);
                    if (StrUtil.isNotBlank(clientHeader)) {
                        try {
                            Long requestedId = Long.parseLong(clientHeader);
                            if (!requestedId.equals(authTenantId)) {
                                log.warn("🛑 [CROSS_TENANT_ATTACK_BLOCKED] 检测到跨租户越权攻击！会话租户={}, 试图伪造租户={}, 用户={}",
                                        authTenantId, requestedId, StpUtil.getLoginIdDefaultNull());
                                throw new BusinessException(ErrorCode.FORBIDDEN, "跨租户安全拦截：严禁越权访问其他租户资产");
                            }
                        } catch (NumberFormatException e) {
                            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法的租户标识格式");
                        }
                    }

                    TenantContextHolder.setTenantId(authTenantId);
                    return true;
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ignored) {
            // 未登录或非登录请求继续往下走公开租户兜底
        }

        // 2. 未登录态接口 (如 /auth/login 等)，尝试解析请求头或默认租户
        String tenantHeader = request.getHeader(HEADER_TENANT_ID);
        if (StrUtil.isNotBlank(tenantHeader)) {
            try {
                TenantContextHolder.setTenantId(Long.parseLong(tenantHeader));
                return true;
            } catch (NumberFormatException ignored) {
            }
        }

        String tenantParam = request.getParameter(PARAM_TENANT_ID);
        if (StrUtil.isNotBlank(tenantParam)) {
            try {
                TenantContextHolder.setTenantId(Long.parseLong(tenantParam));
                return true;
            } catch (NumberFormatException ignored) {
            }
        }

        // 3. 兜底为系统默认租户
        TenantContextHolder.setTenantId(TenantContextHolder.DEFAULT_TENANT_ID);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束务必清理 ThreadLocal，防止 Tomcat/虚拟线程池线程复用导致数据串租户
        TenantContextHolder.clear();
    }
}

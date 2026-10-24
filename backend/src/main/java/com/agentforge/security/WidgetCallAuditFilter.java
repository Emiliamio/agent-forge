package com.agentforge.security;

import cn.hutool.crypto.digest.DigestUtil;
import com.agentforge.config.AsyncAuditConfig;
import com.agentforge.entity.WidgetCallAudit;
import com.agentforge.mapper.WidgetCallAuditMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公开挂件调用全量审计拦截器
 * 拦截 /public/agents/{id}/chat 请求，精准记录调用状态 (outcome)、耗时 (latency_ms) 与脱敏身份哈希 (ip_hash/ua_hash)
 * 异步非阻塞写入数据库，任何写库异常均安全捕获并仅记录 log.warn，100% 杜绝因审计异常阻塞或破坏正常业务响应
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class WidgetCallAuditFilter extends OncePerRequestFilter {

    private static final Pattern AGENT_ID_PATTERN = Pattern.compile("/public/agents/(\\d+)/chat");

    private final WidgetCallAuditMapper widgetCallAuditMapper;
    private final ClientIpResolver clientIpResolver;
    private final Executor widgetAuditExecutor;

    @Value("${agentforge.security.audit-salt:agentforge-audit-salt-2026-mio}")
    private String auditSalt;

    public WidgetCallAuditFilter(
            WidgetCallAuditMapper widgetCallAuditMapper,
            ClientIpResolver clientIpResolver,
            @Qualifier(AsyncAuditConfig.AUDIT_EXECUTOR_NAME) Executor widgetAuditExecutor
    ) {
        this.widgetCallAuditMapper = widgetCallAuditMapper;
        this.clientIpResolver = clientIpResolver;
        this.widgetAuditExecutor = widgetAuditExecutor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        if (path.startsWith("/api/")) {
            path = path.substring(4);
        }
        return !(path.startsWith("/public/agents/") && path.endsWith("/chat"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNano = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            int latencyMs = (int) Math.max(0, (System.nanoTime() - startNano) / 1_000_000L);
            asyncRecordAudit(request, response, latencyMs);
        }
    }

    private void asyncRecordAudit(HttpServletRequest request, HttpServletResponse response, int latencyMs) {
        try {
            // 1. 提取 Trace ID 与 Agent ID
            String traceId = request.getHeader("X-Request-Id");
            if (traceId == null || traceId.isBlank()) {
                traceId = request.getHeader("X-Trace-Id");
            }
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }

            long agentId = 0L;
            String uri = request.getRequestURI();
            if (uri != null) {
                Matcher matcher = AGENT_ID_PATTERN.matcher(uri);
                if (matcher.find()) {
                    try {
                        agentId = Long.parseLong(matcher.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // 2. 脱敏计算 ip_hash 与 ua_hash (SHA-256 固定 64 位十六进制，零明文留痕)
            String rawIp = clientIpResolver != null ? clientIpResolver.resolveClientIp(request) : request.getRemoteAddr();
            if (rawIp == null || rawIp.isBlank()) {
                rawIp = "127.0.0.1";
            }
            String ipHash = DigestUtil.sha256Hex(auditSalt + ":" + rawIp.trim());

            String userAgent = request.getHeader("User-Agent");
            String uaHash = DigestUtil.sha256Hex(userAgent != null ? userAgent : "");

            // 3. 判定 Outcome 归宿
            int httpStatus = response.getStatus();
            String outcome = (String) request.getAttribute("widget_audit_outcome");
            if (outcome == null || outcome.isBlank()) {
                if (httpStatus >= 200 && httpStatus < 300) {
                    outcome = "ok";
                } else if (httpStatus == 429) {
                    outcome = "rate_limited";
                } else if (httpStatus == 401 || httpStatus == 403) {
                    outcome = "invalid_token";
                } else if (httpStatus == 400 || httpStatus == 404) {
                    outcome = "bad_request";
                } else {
                    outcome = "error";
                }
            }

            final String finalTraceId = traceId;
            final long finalAgentId = agentId;
            final String finalIpHash = ipHash;
            final String finalUaHash = uaHash;
            final String finalOutcome = outcome;
            final int finalHttpStatus = httpStatus;
            final int finalLatencyMs = latencyMs;

            widgetAuditExecutor.execute(() -> {
                try {
                    // 支持 L1 反向验证注入模拟异常
                    if (Boolean.getBoolean("agentforge.test.simulate-audit-failure")) {
                        throw new RuntimeException("Simulated Audit Failure for L1 verification");
                    }

                    WidgetCallAudit audit = WidgetCallAudit.builder()
                            .traceId(finalTraceId)
                            .agentId(finalAgentId)
                            .ipHash(finalIpHash)
                            .uaHash(finalUaHash)
                            .outcome(finalOutcome)
                            .httpStatus(finalHttpStatus)
                            .latencyMs(finalLatencyMs)
                            .createdAt(LocalDateTime.now())
                            .build();

                    widgetCallAuditMapper.insert(audit);
                } catch (Throwable t) {
                    log.warn("⚠️ [AUDIT_WRITE_FAIL] 写入公开挂件调用审计日志失败: {}", t.getMessage(), t);
                }
            });
        } catch (Throwable t) {
            log.warn("⚠️ [AUDIT_DISPATCH_FAIL] 调度公开挂件调用审计任务失败: {}", t.getMessage());
        }
    }
}

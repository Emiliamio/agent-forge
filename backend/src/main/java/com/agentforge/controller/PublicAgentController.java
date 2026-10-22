package com.agentforge.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.AgentApp;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.security.ClientIpResolver;
import com.agentforge.security.LuaRateLimiter;
import com.agentforge.security.ReplayGuard;
import com.agentforge.security.SignedWidgetToken;
import com.agentforge.service.agent.AgentAppService;
import com.agentforge.service.agent.ReActAgentService;
import com.agentforge.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 专为第三方系统与 Web Component 嵌入式挂件 (agentforge-widget.js) 提供的免登录公开会话接口。
 * 内置跨租户安全寻址、严格默认关闭 (public_enabled=0)、常量时间 Token 防时序攻击与原子多维度组合限流 (IP 30 / Agent 300 / Token 60)。
 */
@Slf4j
@Tag(name = "07-P. 挂件公开对话接口", description = "支持第三方两行代码嵌入式 Web Component 智能助理挂件")
@RestController
@RequestMapping("/public/agents")
@RequiredArgsConstructor
public class PublicAgentController {

    private final AgentAppService agentAppService;
    private final ReActAgentService reActAgentService;
    private final ReplayGuard replayGuard;
    private final LuaRateLimiter luaRateLimiter;
    private final ClientIpResolver clientIpResolver;

    @org.springframework.beans.factory.annotation.Value("${agentforge.widget.rate-limit-rpm:30}")
    private int ipRateLimitRpm = 30;

    @org.springframework.beans.factory.annotation.Value("${agentforge.widget.rate-limit-agent-rpm:300}")
    private int agentRateLimitRpm = 300;

    @org.springframework.beans.factory.annotation.Value("${agentforge.widget.rate-limit-token-rpm:60}")
    private int tokenRateLimitRpm = 60;

    @org.springframework.beans.factory.annotation.Value("${agentforge.widget.legacy-token-enabled:true}")
    private boolean legacyTokenEnabled = true;

    @jakarta.annotation.PostConstruct
    public void init() {
        if (legacyTokenEnabled) {
            log.warn("Sunset 2027-01-01: Legacy plaintext public widget tokens are currently enabled. Please upgrade client integrations to signed widget tokens (v1 format).");
        }
    }

    public void setLegacyTokenEnabled(boolean legacyTokenEnabled) {
        this.legacyTokenEnabled = legacyTokenEnabled;
    }

    @Operation(summary = "挂件免登录对话 (支持跨租户定位、安全开关与常量时间凭据校验)")
    @PostMapping("/{id}/chat")
    public Result<ReActAgentService.AgentExecutionResult> widgetChat(
            @PathVariable Long id,
            @Valid @RequestBody WidgetChatRequest request,
            @RequestHeader(value = "X-Public-Token", required = false) String headerToken,
            @RequestHeader(value = "X-AgentForge-Public-Token", required = false) String legacyToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest servletRequest
    ) {
        // 1. 提取客户端真实 IP 与传入 Token
        String ip = clientIpResolver != null ? clientIpResolver.resolveClientIp(servletRequest) : "127.0.0.1";

        String incomingToken = null;
        if (StrUtil.isNotBlank(headerToken)) {
            incomingToken = headerToken.trim();
        } else if (StrUtil.isNotBlank(legacyToken)) {
            incomingToken = legacyToken.trim();
        } else if (StrUtil.isNotBlank(authHeader)) {
            incomingToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
        } else if (request != null && StrUtil.isNotBlank(request.getToken())) {
            incomingToken = request.getToken().trim();
        }

        // 2. 多维度组合原子限流防护 (IP 维度 30 RPM, Agent 维度 300 RPM, Token 维度 60 RPM)
        checkMultiDimensionRateLimit(ip, id, incomingToken);

        // 3. 跨租户物理检索智能体元数据 (忽略拦截器对未登录默认租户 1 的硬过滤，精准定位目标租户)
        AgentApp app = agentAppService.findPublicApp(id);
        if (app == null || app.getStatus() == null || app.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AGENT_APP_NOT_FOUND, "目标智能体不存在或已被停用");
        }

        // 4. 安全授权边界：严格默认安全 (必须显式开启 public_enabled == 1，null 或 0 均坚决拒绝)
        if (app.getPublicEnabled() == null || app.getPublicEnabled() != 1) {
            log.warn("🛑 [PUBLIC_WIDGET_DISABLED] 拒绝访问：智能体 appId={}, tenantId={} 未开启公开挂件权限", id, app.getTenantId());
            throw new BusinessException(ErrorCode.FORBIDDEN, "目标智能体未开启公开挂件访问权限");
        }

        // 5. 安全 Token 校验 (支持 v1 签名令牌与明文兼容，支持双阶段密钥轮换，防时序侧信道攻击)
        boolean hasTokenConfigured = StrUtil.isNotBlank(app.getPublicToken()) || StrUtil.isNotBlank(app.getPublicTokenPrev());
        if (hasTokenConfigured) {
            if (StrUtil.isBlank(incomingToken)) {
                log.warn("🛑 [PUBLIC_WIDGET_AUTH_FAIL] 挂件凭证缺失：appId={}", id);
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证 (Token) 无效或缺失");
            }

            if (SignedWidgetToken.isSignedToken(incomingToken)) {
                SignedWidgetToken.verify(incomingToken, app.getPublicToken(), app.getPublicTokenPrev(), app.getId(), app.getTenantId(), replayGuard);
            } else {
                if (!legacyTokenEnabled) {
                    log.warn("🛑 [PUBLIC_WIDGET_LEGACY_DISABLED] 明文挂件令牌已被安全策略禁用：appId={}", id);
                    throw new BusinessException(ErrorCode.UNAUTHORIZED, "明文令牌已停用，请升级 v1 签名令牌");
                }
                log.warn("Legacy plaintext public token used for agent {}, please upgrade to signed tokens", id);
                boolean tokenMatches = false;
                if (StrUtil.isNotBlank(app.getPublicToken())) {
                    tokenMatches = MessageDigest.isEqual(
                            app.getPublicToken().getBytes(StandardCharsets.UTF_8),
                            incomingToken.getBytes(StandardCharsets.UTF_8)
                    );
                }
                if (!tokenMatches && StrUtil.isNotBlank(app.getPublicTokenPrev())) {
                    tokenMatches = MessageDigest.isEqual(
                            app.getPublicTokenPrev().getBytes(StandardCharsets.UTF_8),
                            incomingToken.getBytes(StandardCharsets.UTF_8)
                    );
                }
                if (!tokenMatches) {
                    log.warn("🛑 [PUBLIC_WIDGET_AUTH_FAIL] 挂件凭证校验失败：appId={}", id);
                    throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证 (Token) 无效或缺失");
                }
            }
        }

        // 6. 动态精准切换至智能体所属真实租户上下文受控执行，执行完成后安全还原
        Long originalTenantId = TenantContextHolder.getTenantId();
        try {
            TenantContextHolder.setTenantId(app.getTenantId());
            int maxIterations = request.getMaxIterations() > 0 ? Math.min(request.getMaxIterations(), 5) : 3;
            ReActAgentService.AgentExecutionResult result = reActAgentService.runAgent(id, request.getMessage(), maxIterations);
            return Result.success("对话完成", result);
        } finally {
            if (originalTenantId != null) {
                TenantContextHolder.setTenantId(originalTenantId);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    private void checkMultiDimensionRateLimit(String ip, Long agentId, String incomingToken) {
        if (luaRateLimiter == null) {
            return;
        }

        // 维度 1: IP 速率限制 (默认 30 RPM)
        LuaRateLimiter.RateLimitResult ipResult = luaRateLimiter.tryAcquire("widget:rate:ip:" + ip, ipRateLimitRpm, 60);
        if (ipResult != null && !ipResult.isAllowed()) {
            log.warn("🛑 [RATE_LIMIT_IP] 挂件单 IP 访问频次超限：ip={}, current={}, limit={}", ip, ipResult.getCurrent(), ipResult.getLimit());
            long reset = (System.currentTimeMillis() / 1000L) + ipResult.getTtlSeconds();
            throw new BusinessException(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "挂件单 IP 提问频次过高，请稍后再试",
                    (int) ipResult.getTtlSeconds(),
                    ipResult.getLimit(),
                    0L,
                    reset
            );
        }

        // 维度 2: Agent 实例整体速率限制 (防多 IP 刷爆单一 Agent，默认 300 RPM)
        LuaRateLimiter.RateLimitResult agentResult = luaRateLimiter.tryAcquire("widget:rate:agent:" + agentId, agentRateLimitRpm, 60);
        if (agentResult != null && !agentResult.isAllowed()) {
            log.warn("🛑 [RATE_LIMIT_AGENT] 智能体全量访问频次超限：agentId={}, current={}, limit={}", agentId, agentResult.getCurrent(), agentResult.getLimit());
            long reset = (System.currentTimeMillis() / 1000L) + agentResult.getTtlSeconds();
            throw new BusinessException(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "目标智能体当前访问并发量过高，请稍后再试",
                    (int) agentResult.getTtlSeconds(),
                    agentResult.getLimit(),
                    0L,
                    reset
            );
        }

        // 维度 3: Token 维度速率限制 (若传入有效 Token，默认 60 RPM)
        if (StrUtil.isNotBlank(incomingToken)) {
            String tokenHash = DigestUtil.sha256Hex(incomingToken);
            LuaRateLimiter.RateLimitResult tokenResult = luaRateLimiter.tryAcquire("widget:rate:token:" + tokenHash, tokenRateLimitRpm, 60);
            if (tokenResult != null && !tokenResult.isAllowed()) {
                log.warn("🛑 [RATE_LIMIT_TOKEN] 挂件凭证调用频次超限：tokenHash={}, current={}, limit={}", tokenHash, tokenResult.getCurrent(), tokenResult.getLimit());
                long reset = (System.currentTimeMillis() / 1000L) + tokenResult.getTtlSeconds();
                throw new BusinessException(
                        ErrorCode.RATE_LIMIT_EXCEEDED,
                        "挂件专属凭证调用频次超限，请稍后再试",
                        (int) tokenResult.getTtlSeconds(),
                        tokenResult.getLimit(),
                        0L,
                        reset
                );
            }
        }
    }

    @Data
    public static class WidgetChatRequest {
        @NotBlank(message = "提问内容不能为空")
        private String message;
        private int maxIterations = 3;
        private String token; // 安全挂件访问凭证
    }
}

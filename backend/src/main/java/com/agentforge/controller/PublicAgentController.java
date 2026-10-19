package com.agentforge.controller;

import cn.hutool.core.util.StrUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.AgentApp;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.AgentAppMapper;
import com.agentforge.security.ClientIpResolver;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * 专为第三方系统与 Web Component 嵌入式挂件 (agentforge-widget.js) 提供的免登录公开会话接口。
 * 内置跨租户安全寻址、严格默认关闭 (public_enabled=0)、常量时间 Token 防时序攻击与防伪造 IP 速率限制防护 (30 次/分钟)。
 */
@Slf4j
@Tag(name = "07-P. 挂件公开对话接口", description = "支持第三方两行代码嵌入式 Web Component 智能助理挂件")
@RestController
@RequestMapping("/public/agents")
@RequiredArgsConstructor
public class PublicAgentController {

    private final AgentAppMapper agentAppMapper;
    private final ReActAgentService reActAgentService;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "挂件免登录对话 (支持跨租户定位、安全开关与常量时间凭据校验)")
    @PostMapping("/{id}/chat")
    public Result<ReActAgentService.AgentExecutionResult> widgetChat(
            @PathVariable Long id,
            @Valid @RequestBody WidgetChatRequest request,
            @RequestHeader(value = "X-Public-Token", required = false) String headerToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest servletRequest
    ) {
        // 1. 单 IP 速率防护 (从右向左安全剥离反代，直连杜绝 Header 伪造绕过，限制 30 RPM)
        checkRateLimit(servletRequest);

        // 2. 跨租户物理检索智能体元数据 (忽略拦截器对未登录默认租户 1 的硬过滤，精准定位目标租户)
        AgentApp app = agentAppMapper.selectByIdWithoutTenant(id);
        if (app == null || app.getStatus() == null || app.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AGENT_APP_NOT_FOUND, "目标智能体不存在或已被停用");
        }

        // 3. 安全授权边界：严格默认安全 (必须显式开启 public_enabled == 1，null 或 0 均坚决拒绝)
        if (app.getPublicEnabled() == null || app.getPublicEnabled() != 1) {
            log.warn("🛑 [PUBLIC_WIDGET_DISABLED] 拒绝访问：智能体 appId={}, tenantId={} 未开启公开挂件权限", id, app.getTenantId());
            throw new BusinessException(ErrorCode.FORBIDDEN, "目标智能体未开启公开挂件访问权限");
        }

        // 4. 安全 Token 校验 (常量时间比对防时序侧信道攻击，禁止从 Query String 传输防日志泄露)
        if (StrUtil.isNotBlank(app.getPublicToken())) {
            String incomingToken = StrUtil.isNotBlank(headerToken) ? headerToken : request.getToken();
            if (StrUtil.isBlank(incomingToken) && StrUtil.isNotBlank(authHeader)) {
                incomingToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
            }

            boolean tokenMatches = false;
            if (StrUtil.isNotBlank(incomingToken)) {
                tokenMatches = MessageDigest.isEqual(
                        app.getPublicToken().getBytes(StandardCharsets.UTF_8),
                        incomingToken.trim().getBytes(StandardCharsets.UTF_8)
                );
            }

            if (!tokenMatches) {
                log.warn("🛑 [PUBLIC_WIDGET_AUTH_FAIL] 挂件凭证校验失败：appId={}", id);
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证 (Token) 无效或缺失");
            }
        }

        // 5. 动态精准切换至智能体所属真实租户上下文受控执行，执行完成后安全还原
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

    private void checkRateLimit(HttpServletRequest request) {
        String ip = clientIpResolver != null ? clientIpResolver.resolveClientIp(request) : "127.0.0.1";
        String key = "widget:ratelimit:" + ip;
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                Long count = redis.opsForValue().increment(key);
                if (count != null && count == 1) {
                    redis.expire(key, Duration.ofMinutes(1));
                }
                if (count != null && count > 30) {
                    throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "挂件提问频次过高，请稍后再试");
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                log.warn("挂件限流 Redis 检查降级: {}", e.getMessage());
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

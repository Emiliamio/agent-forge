package com.agentforge.controller;

import cn.hutool.core.util.StrUtil;
import com.agentforge.exception.ErrorCode;
import com.agentforge.vo.Result;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.AgentApp;
import com.agentforge.exception.BusinessException;
import com.agentforge.mapper.AgentAppMapper;
import com.agentforge.service.agent.ReActAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * 专为第三方系统与 Web Component 嵌入式挂件 (agentforge-widget.js) 提供的免登录公开会话接口。
 * 内置多租户安全寻址、公开授权开关 (public_enabled)、可选安全 Token 签名以及防伪造 IP 速率限制防护 (30 次/分钟)。
 */
@Slf4j
@Tag(name = "07-P. 挂件公开对话接口", description = "支持第三方无鉴权两行代码嵌入式 Web Component 智能助理挂件")
@RestController
@RequestMapping("/public/agents")
@RequiredArgsConstructor
public class PublicAgentController {

    private final AgentAppMapper agentAppMapper;
    private final ReActAgentService reActAgentService;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Operation(summary = "挂件免登录对话 (支持跨租户定位、公开开关与防伪造限流)")
    @PostMapping("/{id}/chat")
    public Result<ReActAgentService.AgentExecutionResult> widgetChat(
            @PathVariable Long id,
            @RequestBody WidgetChatRequest request,
            @RequestParam(value = "token", required = false) String paramToken,
            @RequestHeader(value = "X-Public-Token", required = false) String headerToken,
            HttpServletRequest servletRequest
    ) {
        // 1. 单 IP 速率防护 (防恶意刷爆 LLM Token，限制 30 RPM，防伪造 IP 欺骗)
        checkRateLimit(servletRequest);

        // 2. 跨租户物理检索智能体元数据 (忽略拦截器对未登录默认租户 1 的硬过滤，精准定位目标租户)
        AgentApp app = agentAppMapper.selectByIdWithoutTenant(id);
        if (app == null || app.getStatus() == null || app.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AGENT_APP_NOT_FOUND, "目标智能体不存在或已被停用");
        }

        // 3. 安全授权边界：检查目标智能体是否开启了公开挂件访问
        if (app.getPublicEnabled() != null && app.getPublicEnabled() != 1) {
            log.warn("🛑 [PUBLIC_WIDGET_DISABLED] 拒绝访问：智能体 appId={}, tenantId={} 未开启公开挂件权限", id, app.getTenantId());
            throw new BusinessException(ErrorCode.FORBIDDEN, "目标智能体未开启公开挂件访问权限");
        }

        // 4. 可选安全 Token 签名校验 (若后台配置了 public_token，则必须匹配)
        if (StrUtil.isNotBlank(app.getPublicToken())) {
            String incomingToken = StrUtil.isNotBlank(headerToken) ? headerToken :
                    (StrUtil.isNotBlank(paramToken) ? paramToken : request.getToken());
            if (!app.getPublicToken().equals(incomingToken)) {
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
        String ip = extractClientIp(request);
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

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String realIp = request.getHeader("X-Real-IP");
            if (StrUtil.isNotBlank(realIp) && !"unknown".equalsIgnoreCase(realIp)) {
                return realIp.trim();
            }
            String xff = request.getHeader("X-Forwarded-For");
            if (StrUtil.isNotBlank(xff) && !"unknown".equalsIgnoreCase(xff)) {
                String[] hops = xff.split(",");
                return hops[hops.length - 1].trim();
            }
        }
        return remoteAddr != null ? remoteAddr : "127.0.0.1";
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        return ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")
                || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    @Data
    public static class WidgetChatRequest {
        @NotBlank(message = "提问内容不能为空")
        private String message;
        private int maxIterations = 3;
        private String token; // 可选的挂件签名凭证
    }
}

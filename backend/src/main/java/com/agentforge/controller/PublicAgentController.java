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
 * 内置单 IP 速率限制防护 (30 次/分钟)，并在目标智能体绑定的租户隔离上下文中安全执行。
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

    @Operation(summary = "挂件免登录对话 (两行代码极速嵌入专用)")
    @PostMapping("/{id}/chat")
    public Result<ReActAgentService.AgentExecutionResult> widgetChat(
            @PathVariable Long id,
            @RequestBody WidgetChatRequest request,
            HttpServletRequest servletRequest
    ) {
        // 1. 单 IP 速率防护 (防恶意刷爆 LLM Token，限制 30 RPM)
        checkRateLimit(servletRequest);

        // 2. 校验目标智能体是否存在且可用
        AgentApp app = agentAppMapper.selectById(id);
        if (app == null || app.getStatus() == null || app.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AGENT_APP_NOT_FOUND, "目标智能体不存在或已被停用");
        }

        // 3. 动态切换至智能体所属租户上下文受控执行，执行完成后安全还原
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
        String ip = request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
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
    }
}

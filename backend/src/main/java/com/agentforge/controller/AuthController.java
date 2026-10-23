package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.agentforge.dto.LoginRequest;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.service.auth.AuthQueryService;
import com.agentforge.vo.LoginVO;
import com.agentforge.vo.SysTenantVo;
import com.agentforge.vo.SysUserVo;
import com.agentforge.vo.Result;
import com.agentforge.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 身份认证与用户权限接口
 */
@Slf4j
@Tag(name = "02. 身份认证与权限", description = "提供多租户登录、退出与当前用户信息获取")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthQueryService authQueryService;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final HttpServletRequest servletRequest;
    private final com.agentforge.security.ClientIpResolver clientIpResolver;

    // 登录防暴力破解限流器 (1 分钟内同一 IP + 用户名最多尝试 5 次)
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 60L;
    private static final long WINDOW_MS = 60_000L;
    private final Map<String, Queue<Long>> inMemoryAttemptMap = new ConcurrentHashMap<>();

    private String buildRateLimitKey(String username) {
        String ip = clientIpResolver != null ? clientIpResolver.resolveClientIp(servletRequest) : "127.0.0.1";
        return "auth:ratelimit:login:" + ip + ":" + (username != null ? username.trim() : "anonymous");
    }

    private void checkLoginRateLimit(String rateLimitKey) {
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                Long attempts = redis.opsForValue().increment(rateLimitKey);
                if (attempts != null && attempts == 1) {
                    redis.expire(rateLimitKey, Duration.ofSeconds(WINDOW_SECONDS));
                }
                if (attempts != null && attempts > MAX_LOGIN_ATTEMPTS) {
                    log.warn("🛑 [LOGIN_BRUTE_FORCE_BLOCKED] Redis 检测到暴力破解或高频请求，触发安全限流拦截！key={}", rateLimitKey);
                    throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "登录尝试过于频繁，已触发防爆破风控保护，请 1 分钟后再试");
                }
                return;
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                log.warn("Redis 限流探针异常，自动平滑降级至内存限流: {}", e.getMessage());
            }
        }

        // 内存兜底降级
        long now = System.currentTimeMillis();
        inMemoryAttemptMap.compute(rateLimitKey, (key, queue) -> {
            if (queue == null) {
                queue = new ConcurrentLinkedQueue<>();
            }
            while (!queue.isEmpty() && now - queue.peek() > WINDOW_MS) {
                queue.poll();
            }
            if (queue.size() >= MAX_LOGIN_ATTEMPTS) {
                log.warn("🛑 [LOGIN_BRUTE_FORCE_BLOCKED] 内存限流拦截！key={}", rateLimitKey);
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "登录尝试过于频繁，已触发防爆破风控保护，请 1 分钟后再试");
            }
            queue.offer(now);
            return queue;
        });
    }

    private void clearLoginRateLimit(String rateLimitKey) {
        try {
            StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
            if (redis != null) {
                redis.delete(rateLimitKey);
            }
        } catch (Exception ignored) {
        }
        inMemoryAttemptMap.remove(rateLimitKey);
    }

    @Operation(summary = "用户登录 (支持多租户)")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        String tenantCode = StrUtil.isNotBlank(request.getTenantCode()) ? request.getTenantCode() : "tenant_default";
        String rateLimitKey = buildRateLimitKey(request.getUsername());

        // 0. 安全防爆破限流校验 (基于 IP + 用户名)
        checkLoginRateLimit(rateLimitKey);

        // 1. 查询租户
        SysTenantVo tenant = authQueryService.findTenantVoByCode(tenantCode);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }

        // 2. 查询用户 (必须在该租户下)
        SysUserVo user = authQueryService.findUserVoByTenantAndUsername(tenant.getId(), request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // 3. 校验密码 (支持 BCrypt 或初始明文兼容)
        boolean isMatch = false;
        try {
            isMatch = BCrypt.checkpw(request.getPassword(), user.getPasswordHash());
        } catch (Exception e) {
            // 兼容性校验
            isMatch = request.getPassword().equals(user.getPasswordHash());
        }

        if (!isMatch) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // 4. 执行 Sa-Token 登录，附加租户与角色信息到 Session 中，全面兼容 Redis 与内存存储
        StpUtil.login(user.getId());
        StpUtil.getSession().set("tenantId", tenant.getId()).set("role", user.getRole());

        // 5. 登录成功，立即销毁防爆破计数，杜绝针对合法用户的账号级 DoS
        clearLoginRateLimit(rateLimitKey);

        LoginVO loginVO = LoginVO.builder()
                .token(StpUtil.getTokenValue())
                .tokenPrefix("Bearer")
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .username(user.getUsername())
                .role(user.getRole())
                .build();

        return Result.success("登录成功", loginVO);
    }

    @Operation(summary = "获取当前用户信息与租户白牌配置")
    @SaCheckLogin
    @GetMapping("/user-info")
    public Result<UserInfoVO> getUserInfo() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUserVo user = authQueryService.findUserVoById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        SysTenantVo tenant = authQueryService.findTenantVoById(user.getTenantId());

        UserInfoVO vo = UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(user.getTenantId())
                .tenantName(tenant != null ? tenant.getName() : "默认租户")
                .walletBalance(tenant != null ? tenant.getWalletBalance() : null)
                .whiteLabelConfig(tenant != null ? tenant.getWhiteLabelConfig() : null)
                .build();

        return Result.success(vo);
    }

    @Operation(summary = "用户登出")
    @SaCheckLogin
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success("已成功退出登录", null);
    }
}

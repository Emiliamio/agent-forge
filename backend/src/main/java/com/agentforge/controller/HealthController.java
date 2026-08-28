package com.agentforge.controller;

import com.agentforge.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统健康检查与基础设施状态监控接口
 */
@Tag(name = "01. 系统健康监控", description = "用于检查容器环境、PostgreSQL+pgvector 与 Redis 运行状态")
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Operation(summary = "系统全链路健康检查")
    @GetMapping("/health")
    public Result<Map<String, Object>> checkHealth() {
        Map<String, Object> status = new HashMap<>();
        status.put("appName", "AgentForge Backend");
        status.put("version", "1.0.0");
        status.put("javaVersion", System.getProperty("java.version"));
        status.put("isVirtualThreads", Thread.currentThread().isVirtual());
        status.put("timestamp", System.currentTimeMillis());

        // 1. 检查 PostgreSQL 与 pgvector 扩展
        try {
            String vectorExt = jdbcTemplate.queryForObject(
                    "SELECT extname FROM pg_extension WHERE extname = 'vector'", String.class);
            status.put("postgres", "UP");
            status.put("pgvectorExtension", vectorExt != null ? "AVAILABLE" : "MISSING");
        } catch (Exception e) {
            status.put("postgres", "DOWN: " + e.getMessage());
            status.put("pgvectorExtension", "UNAVAILABLE");
        }

        // 2. 检查 Redis 连通性
        try {
            String pingResult = redisTemplate.getConnectionFactory().getConnection().ping();
            status.put("redis", "PONG".equalsIgnoreCase(pingResult) ? "UP" : "DOWN");
        } catch (Exception e) {
            status.put("redis", "DOWN: " + e.getMessage());
        }

        return Result.success("AgentForge 核心系统服务正常", status);
    }
}

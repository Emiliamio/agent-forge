package com.agentforge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 挂件调用审计专用异步线程池配置
 * 避免使用默认 SimpleAsyncTaskExecutor 导致的高并发线程暴涨风险
 * 采用固定 2 线程 + 有界队列 1000 + 满载丢弃并告警策略，彻底保障业务核心主线程零阻塞
 */
@Slf4j
@Configuration
public class AsyncAuditConfig {

    public static final String AUDIT_EXECUTOR_NAME = "widgetAuditExecutor";

    @Bean(name = AUDIT_EXECUTOR_NAME)
    public Executor widgetAuditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("widget-audit-");
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("⚠️ [AUDIT_QUEUE_FULL] 挂件调用审计线程池队列已满（1000），丢弃当前审计任务以保全核心业务")
        );
        executor.initialize();
        return executor;
    }
}

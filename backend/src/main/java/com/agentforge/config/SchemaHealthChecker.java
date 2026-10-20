package com.agentforge.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库表结构健康状态巡检器 (SchemaHealthChecker)
 * 校验 flyway_schema_history、agent_app.public_enabled、document_chunk.tsv_content 是否齐备，
 * 若缺失则坚决拒绝启动 (fail-closed) 并提示执行 mvn flyway:migrate
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class SchemaHealthChecker implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("🩺 [SchemaHealthChecker] 启动数据库表结构与关键字段健康状态巡检...");

        // 1. 校验 flyway_schema_history 存在
        Integer flywayTable = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'",
                Integer.class
        );
        if (flywayTable == null || flywayTable == 0) {
            throw new IllegalStateException("Schema health check failed: missing required table 'flyway_schema_history'. Please run 'mvn flyway:migrate'");
        }

        // 2. 校验 agent_app.public_enabled 存在
        Integer publicEnabledCol = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'agent_app' AND column_name = 'public_enabled'",
                Integer.class
        );
        if (publicEnabledCol == null || publicEnabledCol == 0) {
            throw new IllegalStateException("Schema health check failed: missing required column 'agent_app.public_enabled'. Please run 'mvn flyway:migrate'");
        }

        // 3. 校验 document_chunk.tsv_content 存在
        Integer tsvCol = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'document_chunk' AND column_name = 'tsv_content'",
                Integer.class
        );
        if (tsvCol == null || tsvCol == 0) {
            throw new IllegalStateException("Schema health check failed: missing required column 'document_chunk.tsv_content'. Please run 'mvn flyway:migrate'");
        }

        log.info("✅ [SchemaHealthChecker] 数据库表结构与核心业务字段健康状态巡检全部通过！");
    }
}

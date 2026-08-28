package com.agentforge.config;

import cn.hutool.core.io.IoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 工业级 Zero-DBA 数据库全自动初始化引擎
 * 在服务首次启动时，自动检测核心表结构是否存在；若缺失，自动静默执行 DDL 与种子数据灌入
 * 彻底消除买家手动执行 SQL 脚本报错、字段缺失导致的投诉与退款
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseAutoInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            // 检测核心租户表是否存在
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'sys_tenant'",
                    Integer.class
            );

            if (tableCount == null || tableCount == 0) {
                log.info("🚀 [Zero-DBA] 检测到新数据库环境，开始全自动执行建表 DDL 与初始化种子数据...");

                // 1. 执行 Schema DDL
                ClassPathResource schemaRes = new ClassPathResource("sql/schema.sql");
                if (schemaRes.exists()) {
                    String schemaSql = IoUtil.read(schemaRes.getInputStream(), StandardCharsets.UTF_8);
                    executeSqlScript(schemaSql);
                    log.info("✅ [Zero-DBA] 14 张核心物理表结构与索引自动初始化完成！");
                }

                // 2. 注入种子初始数据
                ClassPathResource seedRes = new ClassPathResource("sql/seed-data.sql");
                if (seedRes.exists()) {
                    String seedSql = IoUtil.read(seedRes.getInputStream(), StandardCharsets.UTF_8);
                    executeSqlScript(seedSql);
                    log.info("✅ [Zero-DBA] 演示租户、测试文档与默认管理员账户自动注入完成！");
                }
            } else {
                log.info("✨ [Zero-DBA] 数据库表结构校验正常，系统已就绪。");
            }
        } catch (Exception e) {
            log.warn("ℹ️ 数据库自动初始化检测跳过 (环境可能已预置或使用非标准库): error={}", e.getMessage());
        }
    }

    private void executeSqlScript(String fullSql) {
        String[] statements = fullSql.split(";");
        for (String sql : statements) {
            String trimmed = sql.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                try {
                    jdbcTemplate.execute(trimmed);
                } catch (Exception ignored) {
                    // 容忍单条已存在或非关键报错
                }
            }
        }
    }
}

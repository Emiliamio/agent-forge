package com.agentforge.config;

import cn.hutool.core.io.IoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 工业级 Zero-DBA 数据库全自动初始化引擎
 * 在服务首次启动时，自动检测核心表结构是否存在；若缺失，通过状态机级 SQL 解析器
 * 正确执行包含注释、触发器函数 ($$ 块) 的 PostgreSQL DDL 与种子数据灌入。
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
                    int ddlCount = executeSqlScript(schemaSql, "schema.sql");
                    log.info("✅ [Zero-DBA] 核心物理表结构与索引初始化完成 (共执行 {} 条 DDL/触发器语句)！", ddlCount);
                }

                // 2. 注入种子初始数据
                ClassPathResource seedRes = new ClassPathResource("sql/seed-data.sql");
                if (seedRes.exists()) {
                    String seedSql = IoUtil.read(seedRes.getInputStream(), StandardCharsets.UTF_8);
                    int seedCount = executeSqlScript(seedSql, "seed-data.sql");
                    log.info("✅ [Zero-DBA] 演示租户、测试文档与默认管理员账户注入完成 (共执行 {} 条 DML 语句)！", seedCount);
                }
            } else {
                log.info("✨ [Zero-DBA] 数据库表结构校验正常，系统已就绪。");
            }
        } catch (Exception e) {
            log.error("❌ [Zero-DBA] 数据库自动初始化失败: error={}", e.getMessage(), e);
            throw new IllegalStateException("Zero-DBA 数据库自动建库初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 针对 PostgreSQL 语法特性的状态机 SQL 分解与安全执行器
     * 正确识别：
     * 1. 单行注释 (-- ... \n) 与多行注释 (/* ... *\/)，保留后续主体语句；
     * 2. 单引号字符串 (' ... ') 及转义 ('')；
     * 3. PostgreSQL 函数块 ($$ ... $$) 中的内嵌分号，绝不切断函数体。
     */
    public int executeSqlScript(String fullSql, String scriptName) {
        List<String> statements = splitPostgresSql(fullSql);
        int successCount = 0;

        for (String sql : statements) {
            String trimmed = sql.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            try {
                jdbcTemplate.execute(trimmed);
                successCount++;
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                // 仅容忍明确已存在的索引、扩展或对象，其它错误严禁静默吞掉
                if (errorMsg.contains("already exists") || errorMsg.contains("duplicate key")) {
                    log.debug("ℹ️ [Zero-DBA] 忽略已存在的数据库对象: {}", trimmed.substring(0, Math.min(60, trimmed.length())));
                    successCount++;
                } else {
                    log.error("❌ [Zero-DBA] 执行 SQL 失败 [{}] 语句:\n{}\n错误原因: {}", scriptName, trimmed, e.getMessage());
                    throw new IllegalStateException("SQL 脚本 [" + scriptName + "] 执行致命报错: " + e.getMessage(), e);
                }
            }
        }
        return successCount;
    }

    public static List<String> splitPostgresSql(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDollarQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        int length = script.length();
        for (int i = 0; i < length; i++) {
            char c = script.charAt(i);
            char next = (i + 1 < length) ? script.charAt(i + 1) : '\0';

            if (inLineComment) {
                current.append(c);
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                current.append(c);
                if (c == '*' && next == '/') {
                    current.append('/');
                    i++;
                    inBlockComment = false;
                }
                continue;
            }
            if (inSingleQuote) {
                current.append(c);
                if (c == '\'') {
                    if (next == '\'') {
                        current.append('\'');
                        i++;
                    } else {
                        inSingleQuote = false;
                    }
                }
                continue;
            }
            if (inDollarQuote) {
                current.append(c);
                if (c == '$' && next == '$') {
                    current.append('$');
                    i++;
                    inDollarQuote = false;
                }
                continue;
            }

            if (c == '-' && next == '-') {
                inLineComment = true;
                current.append(c).append(next);
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                current.append(c).append(next);
                i++;
                continue;
            }
            if (c == '\'') {
                inSingleQuote = true;
                current.append(c);
                continue;
            }
            if (c == '$' && next == '$') {
                inDollarQuote = true;
                current.append(c).append(next);
                i++;
                continue;
            }

            if (c == ';') {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            statements.add(remaining);
        }
        return statements;
    }
}

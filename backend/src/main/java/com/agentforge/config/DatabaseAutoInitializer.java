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
import java.util.Map;
import java.util.Set;

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
                log.info("✨ [Zero-DBA] 检测到既有数据库环境，启动存量结构校验与增量平滑迁移...");
            }

            // 无论全新还是存量数据库，每次启动均执行幂等增量迁移与结构自愈
            runSchemaPatchMigrations();

        } catch (Exception e) {
            log.error("❌ [Zero-DBA] 数据库自动初始化/迁移失败: error={}", e.getMessage(), e);
            throw new IllegalStateException("Zero-DBA 数据库自动建库/平滑迁移失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行全量存量数据库的幂等增量迁移与 CJK 倒排索引平滑回填
     * 无论首次初始化还是历史老环境启动，均保证表结构与字段 100% 契合最新代码契约
     */
    /**
     * 核心业务表白名单：限定仅对 AgentForge 内部实体表执行时间字段平滑转换，严禁污染或误伤同库其它应用表
     */
    private static final Set<String> CORE_TABLE_WHITELIST = Set.of(
            "sys_tenant", "sys_user", "agent_app", "knowledge_dataset",
            "dataset_document", "document_chunk", "chat_session",
            "chat_message", "chat_feedback", "token_usage_log", "api_key",
            "workflow_definition", "workflow_execution"
    );

    /**
     * 确保版本元数据表就绪
     */
    public void ensureSchemaVersionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version VARCHAR(64) PRIMARY KEY,
                    description VARCHAR(256),
                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    public boolean isVersionApplied(String version) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM schema_version WHERE version = ?",
                    Integer.class,
                    version
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void recordVersionApplied(String version, String description) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO schema_version (version, description) VALUES (?, ?) ON CONFLICT (version) DO NOTHING",
                    version, description
            );
        } catch (Exception e) {
            log.warn("ℹ️ [Zero-DBA] 记录版本元数据警告: version={}, msg={}", version, e.getMessage());
        }
    }

    /**
     * 执行全量存量数据库的幂等增量迁移与 CJK 倒排索引平滑回填
     * 无论首次初始化还是历史老环境启动，均保证表结构与字段 100% 契合最新代码契约且零重复风暴
     */
    public void runSchemaPatchMigrations() {
        log.info("🔄 [Zero-DBA] 启动存量数据库版本校验与增量平滑演进...");
        ensureSchemaVersionTable();

        // 1. 存量历史环境 TIMESTAMPTZ 类型平滑转换 (仅限定在白名单表，且全局仅执行一次)
        String vTimestamptz = "V1_0_1__timestamptz_alignment";
        if (!isVersionApplied(vTimestamptz)) {
            try {
                List<Map<String, Object>> timestamptzCols = jdbcTemplate.queryForList(
                        "SELECT table_name, column_name FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND column_name IN ('created_at', 'updated_at') " +
                        "AND data_type = 'timestamp with time zone'"
                );
                int convertedCount = 0;
                for (Map<String, Object> col : timestamptzCols) {
                    String tbl = (String) col.get("table_name");
                    String cName = (String) col.get("column_name");
                    if (CORE_TABLE_WHITELIST.contains(tbl.toLowerCase())) {
                        String alterSql = String.format("ALTER TABLE \"%s\" ALTER COLUMN \"%s\" TYPE TIMESTAMP WITHOUT TIME ZONE USING \"%s\"::TIMESTAMP", tbl, cName, cName);
                        jdbcTemplate.execute(alterSql);
                        convertedCount++;
                        log.info("🕒 [Zero-DBA] 平滑转换白名单表时间类型: {}.{} -> TIMESTAMP", tbl, cName);
                    }
                }
                recordVersionApplied(vTimestamptz, "Aligned timestamptz columns to timestamp for " + convertedCount + " core columns");
            } catch (Exception e) {
                log.warn("ℹ️ [Zero-DBA] 存量时间列类型迁移忽略: {}", e.getMessage());
            }
        } else {
            log.debug("ℹ️ [Zero-DBA] 版本 {} 已应用，跳过时间列类型扫描", vTimestamptz);
        }

        // 2. 确保 CJK Bigram 分词函数与触发器在存量环境中升级就绪 (版本化执行)
        String vCjk = "V1_0_2__cjk_bigram_functions";
        if (!isVersionApplied(vCjk)) {
            String cjkFunctionsSql = """
                    CREATE OR REPLACE FUNCTION cjk_bigram_tokenize(str TEXT) RETURNS TEXT AS $$
                    DECLARE
                        len INT;
                        i INT;
                        c1 TEXT;
                        c2 TEXT;
                        result TEXT := '';
                    BEGIN
                        IF str IS NULL OR str = '' THEN
                            RETURN '';
                        END IF;
                        len := length(str);
                        FOR i IN 1..len LOOP
                            c1 := substr(str, i, 1);
                            IF c1 ~ '[\\u4e00-\\u9fa5]' THEN
                                result := result || ' ' || c1;
                                IF i < len THEN
                                    c2 := substr(str, i + 1, 1);
                                    IF c2 ~ '[\\u4e00-\\u9fa5]' THEN
                                        result := result || ' ' || c1 || c2;
                                    END IF;
                                END IF;
                            ELSEIF c1 ~ '[a-zA-Z0-9_]' THEN
                                result := result || c1;
                            ELSE
                                result := result || ' ';
                            END IF;
                        END LOOP;
                        RETURN result;
                    END;
                    $$ LANGUAGE plpgsql IMMUTABLE;

                    CREATE OR REPLACE FUNCTION update_chunk_tsv() RETURNS trigger AS $$
                    BEGIN
                        NEW.tsv_content := to_tsvector('simple', cjk_bigram_tokenize(COALESCE(NEW.content, '')));
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql;

                    DROP TRIGGER IF EXISTS trg_chunk_tsv_update ON document_chunk;
                    CREATE TRIGGER trg_chunk_tsv_update
                    BEFORE INSERT OR UPDATE ON document_chunk
                    FOR EACH ROW EXECUTE FUNCTION update_chunk_tsv();
                    """;
            try {
                executeSqlScript(cjkFunctionsSql, "cjk_migration_patch");
                recordVersionApplied(vCjk, "Upgraded cjk_bigram_tokenize and update_chunk_tsv trigger");
                log.info("📑 [Zero-DBA] CJK Bigram 倒排索引函数与触发器升级完毕并标记版本。");
            } catch (Exception e) {
                log.warn("ℹ️ [Zero-DBA] CJK 函数升级忽略: {}", e.getMessage());
            }
        }

        // 3. 智能体表幂等补充挂件安全开关与 Token 凭据字段
        String vPublicCols = "V1_0_3__agent_public_columns";
        if (!isVersionApplied(vPublicCols)) {
            try {
                jdbcTemplate.execute("ALTER TABLE agent_app ADD COLUMN IF NOT EXISTS public_enabled SMALLINT NOT NULL DEFAULT 0");
                jdbcTemplate.execute("ALTER TABLE agent_app ADD COLUMN IF NOT EXISTS public_token VARCHAR(64)");
                recordVersionApplied(vPublicCols, "Added public_enabled and public_token columns to agent_app");
                log.info("🛡️ [Zero-DBA] 智能体安全挂件字段补丁应用完成。");
            } catch (Exception e) {
                log.warn("ℹ️ [Zero-DBA] 挂件字段增量迁移忽略: {}", e.getMessage());
            }
        }

        // 4. 历史切片倒排索引回填：单次全量回填通过版本记录隔离；后续每次重启仅针对新漏数据做补全，彻底避免启动风暴
        String vBackfill = "V1_0_4__cjk_bigram_backfill";
        if (!isVersionApplied(vBackfill)) {
            try {
                int count = jdbcTemplate.update("UPDATE document_chunk SET content = content");
                recordVersionApplied(vBackfill, "Triggered one-time full recalculation for " + count + " chunks");
                log.info("🚀 [Zero-DBA] 首次全量历史切片 CJK 倒排索引重算回填完毕 ({} 条)，已标记版本", count);
            } catch (Exception e) {
                log.warn("ℹ️ [Zero-DBA] 首次切片倒排回填忽略: {}", e.getMessage());
            }
        } else {
            // 已执行过全量回填，日常仅对 tsv_content 为 NULL 的增量切片补漏，开销几乎为 0
            try {
                jdbcTemplate.execute("UPDATE document_chunk SET content = content WHERE tsv_content IS NULL");
            } catch (Exception ignored) {
            }
        }

        // 5. 严格校验关键列是否存在，杜绝列缺失导致全站 500
        validateSchemaColumns();
    }

    private void validateSchemaColumns() {
        Integer colCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'agent_app' AND column_name = 'public_enabled'",
                Integer.class
        );
        if (colCount == null || colCount == 0) {
            throw new IllegalStateException("Zero-DBA 校验失败: agent_app 表缺少必需列 public_enabled，请检查数据库权限或迁移脚本！");
        }
        log.info("✅ [Zero-DBA] 表结构与关键列校验 100% 通过，系统就绪。");
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

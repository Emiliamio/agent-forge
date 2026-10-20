package com.agentforge.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Flyway 数据库版本化迁移契约测试")
public class FlywayMigrationContractTest {

    private static final Pattern MIGRATION_FILE_PATTERN = Pattern.compile("^V(\\d+)__([a-zA-Z0-9_]+)\\.sql$");

    @Test
    @DisplayName("1. db/migration 下文件名严格匹配 V<数字>__<描述>.sql 且版本连续无空洞")
    void testMigrationFilesStrictNamingAndContinuousVersions() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/*.sql");
        assertTrue(resources.length >= 5, "Flyway 迁移脚本数量应不少于 5 个");

        List<Integer> versions = new ArrayList<>();
        for (Resource res : resources) {
            String filename = res.getFilename();
            assertNotNull(filename);
            Matcher matcher = MIGRATION_FILE_PATTERN.matcher(filename);
            assertTrue(matcher.matches(), "迁移文件名必须符合 V<数字>__<描述>.sql 规范: " + filename);
            versions.add(Integer.parseInt(matcher.group(1)));
        }

        Collections.sort(versions);
        assertEquals(1, versions.get(0), "版本号必须从 1 开始");
        for (int i = 0; i < versions.size(); i++) {
            assertEquals(i + 1, versions.get(i), "版本号必须严格连续无空洞，缺失版本: " + (i + 1));
        }
    }

    @Test
    @DisplayName("2. 每个迁移文件非空且首条有效语句不是 DROP DATABASE / TRUNCATE")
    void testMigrationFilesNotEmptyAndNoDestructiveFirstStatement() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/*.sql");

        for (Resource res : resources) {
            String filename = res.getFilename();
            List<String> validLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--") && !trimmed.startsWith("/*")) {
                        validLines.add(trimmed);
                    }
                }
            }

            assertFalse(validLines.isEmpty(), "迁移文件不能为空: " + filename);
            String firstStmt = validLines.get(0).toUpperCase();
            assertFalse(firstStmt.startsWith("DROP DATABASE"), "严禁以 DROP DATABASE 开头: " + filename);
            assertFalse(firstStmt.startsWith("TRUNCATE"), "严禁以 TRUNCATE 开头: " + filename);
        }
    }

    @Test
    @DisplayName("3. V1__baseline.sql 包含 13 张 CREATE TABLE 且不包含 public_enabled")
    void testV1BaselineStructure() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/V1__*.sql");
        assertEquals(1, resources.length, "必须存在唯一的 V1 基线脚本");

        String content;
        try (var is = resources[0].getInputStream()) {
            content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 校验 13 张 CREATE TABLE
        Matcher tableMatcher = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE).matcher(content);
        int tableCount = 0;
        Set<String> tables = new HashSet<>();
        while (tableMatcher.find()) {
            tableCount++;
            tables.add(tableMatcher.group(1).toLowerCase());
        }
        assertEquals(13, tableCount, "V1__baseline.sql 必须包含 13 张物理表定义");

        // 核心表均应包含
        assertTrue(tables.contains("sys_tenant"));
        assertTrue(tables.contains("agent_app"));
        assertTrue(tables.contains("document_chunk"));

        // 严格断言不包含 public_enabled 字段（交由 V3 显式演进添加）
        assertFalse(content.contains("public_enabled"), "V1__baseline.sql 严禁包含 public_enabled 字段，以保障 V3 独立演进能力");
    }
}

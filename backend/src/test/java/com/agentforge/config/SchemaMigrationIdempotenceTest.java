package com.agentforge.config;

import cn.hutool.core.io.IoUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseAutoInitializer 存量版本化迁移幂等性与核心白名单安全测试")
public class SchemaMigrationIdempotenceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DatabaseAutoInitializer initializer;

    @Test
    @DisplayName("幂等保障与避免启动风暴：当各版本均已应用时，跳过全表重写，仅执行增量维护")
    void testWhenVersionsAppliedThenSkipHeavyMigrations() {
        // 模拟 schema_version 查询均返回 1 (已应用)
        when(jdbcTemplate.queryForObject(
                eq("SELECT count(*) FROM schema_version WHERE version = ?"),
                eq(Integer.class),
                anyString()
        )).thenReturn(1);

        // 模拟关键列校验返回 1 (存在)
        when(jdbcTemplate.queryForObject(
                contains("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'agent_app' AND column_name = 'public_enabled'"),
                eq(Integer.class)
        )).thenReturn(1);

        initializer.runSchemaPatchMigrations();

        // 确保全量无条件更新 UPDATE document_chunk SET content = content 没有被调用（避免启动风暴）
        verify(jdbcTemplate, never()).update("UPDATE document_chunk SET content = content");

        // 确保仅执行了轻量级增量补漏 WHERE tsv_content IS NULL
        verify(jdbcTemplate, atLeastOnce()).execute("UPDATE document_chunk SET content = content WHERE tsv_content IS NULL");
    }

    @Test
    @DisplayName("白名单安全防护：历史 TIMESTAMPTZ 迁移严格限定在 13 张核心表，绝不触碰第三方外部表")
    void testTimestamptzMigrationOnlyTargetsCoreWhitelistTables() {
        // 模拟未应用版本 (返回 0)
        when(jdbcTemplate.queryForObject(
                eq("SELECT count(*) FROM schema_version WHERE version = ?"),
                eq(Integer.class),
                any(Object[].class)
        )).thenReturn(0);

        // 模拟数据库中存在 1 个白名单核心表和 1 个外部第三方表的时间字段
        when(jdbcTemplate.queryForList(contains("FROM information_schema.columns"))).thenReturn(List.of(
                Map.of("table_name", "sys_user", "column_name", "created_at"),
                Map.of("table_name", "third_party_orders", "column_name", "created_at")
        ));

        when(jdbcTemplate.queryForObject(
                contains("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'agent_app' AND column_name = 'public_enabled'"),
                eq(Integer.class)
        )).thenReturn(1);

        initializer.runSchemaPatchMigrations();

        // 校验：白名单表 sys_user 必须执行 ALTER COLUMN
        verify(jdbcTemplate).execute(contains("ALTER TABLE \"sys_user\" ALTER COLUMN \"created_at\" TYPE TIMESTAMP"));

        // 校验：第三方表 third_party_orders 绝对不可执行任何 ALTER 语句
        verify(jdbcTemplate, never()).execute(contains("ALTER TABLE \"third_party_orders\""));
    }

    @Test
    @DisplayName("Fail-Closed 安全自检：若缺少关键列 public_enabled，必须抛出 IllegalStateException 阻止脏启动")
    void testMissingPublicEnabledColumnThrowsException() {
        // 模拟所有版本已应用
        when(jdbcTemplate.queryForObject(
                eq("SELECT count(*) FROM schema_version WHERE version = ?"),
                eq(Integer.class),
                anyString()
        )).thenReturn(1);

        // 关键列校验返回 0 (列缺失)
        when(jdbcTemplate.queryForObject(
                contains("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'agent_app' AND column_name = 'public_enabled'"),
                eq(Integer.class)
        )).thenReturn(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                initializer.runSchemaPatchMigrations()
        );
        assertTrue(ex.getMessage().contains("agent_app 表缺少必需列 public_enabled"));
    }

    @Test
    @DisplayName("白名单表名契约强一致校验：CORE_TABLE_WHITELIST 中声明的全部表名必须严格存在于 schema.sql 中")
    void testCoreTableWhitelistMatchesSchemaSql() throws Exception {
        ClassPathResource res = new ClassPathResource("sql/schema.sql");
        assertTrue(res.exists(), "schema.sql 必须存在于资源目录");
        String sql = IoUtil.read(res.getInputStream(), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-zA-Z0-9_]+)").matcher(sql);
        Set<String> schemaTables = new HashSet<>();
        while (matcher.find()) {
            schemaTables.add(matcher.group(1).toLowerCase());
        }

        Set<String> whitelist = DatabaseAutoInitializer.CORE_TABLE_WHITELIST;
        assertNotNull(whitelist, "白名单表集合不可为空");
        assertFalse(whitelist.isEmpty(), "白名单表集合不可为空");

        // 校验白名单中每一张表都在 schema.sql 中有物理表定义 (杜绝拼写错误如 knowledge_dataset / dataset_document)
        for (String table : whitelist) {
            assertTrue(schemaTables.contains(table.toLowerCase()),
                    "白名单中的表名 [" + table + "] 在 schema.sql 中不存在！可能存在拼写错误导致 TIMESTAMPTZ 无法平滑迁移！");
        }

        // 只做子集断言：白名单是「需要做时间列迁移的核心表」集合，schema.sql 后续新增审计/日志表时
        // 不应强制同步进白名单（否则会误报失败），故这里不断言两者数量完全相等。
        assertTrue(whitelist.size() >= 13,
                "核心白名单不应少于 13 张核心业务表，当前: " + whitelist.size());
    }
}

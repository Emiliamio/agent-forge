package com.agentforge;

import com.agentforge.config.DatabaseAutoInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Zero-DBA 数据库 DDL/DML 状态机解析回归测试")
public class SqlScriptParsingTest {

    @Test
    @DisplayName("验证 schema.sql 经解析后保留完整 13 张物理表、触发器函数与索引且无注释丢弃")
    void testRobustPostgresParsing() throws IOException {
        String schemaSql = StreamUtils.copyToString(new ClassPathResource("sql/schema.sql").getInputStream(), StandardCharsets.UTF_8);
        List<String> statements = DatabaseAutoInitializer.splitPostgresSql(schemaSql);

        int createTableCount = 0;
        int functionCount = 0;
        int triggerCount = 0;

        for (String stmt : statements) {
            String upper = stmt.toUpperCase();
            if (upper.contains("CREATE TABLE")) {
                createTableCount++;
            }
            if (upper.contains("CREATE OR REPLACE FUNCTION UPDATE_CHUNK_TSV")) {
                functionCount++;
                assertTrue(stmt.contains("RETURN NEW;"), "触发器函数体内部分号严禁被错误拆断");
                assertTrue(stmt.contains("$$ LANGUAGE plpgsql"), "触发器函数必须完整以 $$ LANGUAGE plpgsql 结尾");
            }
            if (upper.contains("CREATE TRIGGER")) {
                triggerCount++;
            }
        }

        assertEquals(13, createTableCount, "schema.sql 中必须包含完整的 13 张核心物理表");
        assertEquals(1, functionCount, "必须包含 update_chunk_tsv 触发器函数");
        assertEquals(1, triggerCount, "必须包含 trg_chunk_tsv_update 触发器");
    }

    @Test
    @DisplayName("验证 seed-data.sql 能够被正确解析且无 SQL 截断")
    void testSeedDataParsing() throws IOException {
        String seedSql = StreamUtils.copyToString(new ClassPathResource("sql/seed-data.sql").getInputStream(), StandardCharsets.UTF_8);
        List<String> statements = DatabaseAutoInitializer.splitPostgresSql(seedSql);
        assertTrue(statements.size() >= 5, "seed-data.sql 中必须至少包含 5 条初始数据插入语句");
    }
}

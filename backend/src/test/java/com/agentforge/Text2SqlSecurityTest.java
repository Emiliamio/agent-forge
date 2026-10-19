package com.agentforge;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Text2SQL AST 安全拦截与语法审计测试")
public class Text2SqlSecurityTest {

    @Test
    void testValidSelect() throws Exception {
        String sql = "SELECT region, SUM(sales_amount) FROM sales_record WHERE quarter = '2025Q3' GROUP BY region";
        Statement stmt = CCJSqlParserUtil.parse(sql);
        assertTrue(stmt instanceof Select, "必须为只读 SELECT 语句");

        TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
        List<String> tableList = tablesNamesFinder.getTableList(stmt);
        assertEquals(1, tableList.size());
        assertEquals("sales_record", tableList.get(0).toLowerCase());
    }

    @Test
    void testDangerousSqlBlocked() {
        String dropSql = "DROP TABLE sys_user";
        assertThrows(Exception.class, () -> {
            Statement stmt = CCJSqlParserUtil.parse(dropSql);
            if (!(stmt instanceof Select)) {
                throw new SecurityException("非 SELECT 语句");
            }
        });

        String unionSysUser = "SELECT * FROM sales_record UNION SELECT password_hash FROM sys_user";
        assertDoesNotThrow(() -> {
            Statement stmt = CCJSqlParserUtil.parse(unionSysUser);
            assertTrue(stmt instanceof Select);
            TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
            List<String> tableList = tablesNamesFinder.getTableList(stmt);
            assertTrue(tableList.stream().anyMatch(t -> t.equalsIgnoreCase("sys_user")), "能识别出引用了敏感表 sys_user");
        });
    }
}

package com.agentforge.service.workflow.internal;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.agent.llm.LlmRequest;
import com.agentforge.service.agent.llm.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * TEXT2SQL 商业级智能结构化数据分析节点执行器
 * 采用 JSqlParser AST 语法树级硬核安全审计：
 * 1. 严格只读 SELECT 语义约束；
 * 2. 严禁多语句注入 (;) 与跨表读取用户密码 (sys_user) / API Key / pg_catalog 等敏感表；
 * 3. 阻断高危系统函数 (pg_read_file, pg_sleep, dblink 等)；
 * 4. 强制追加 LIMIT 保护；真实执行异常绝不伪造返回 Mock 数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Text2SqlNodeExecutor implements WorkflowNodeExecutor {

    private final LlmClient llmClient;
    private final JdbcTemplate jdbcTemplate;

    // 严禁查询的敏感与系统底层元数据表
    private static final Set<String> FORBIDDEN_TABLES = Set.of(
            "sys_user", "api_key", "sys_tenant",
            "information_schema", "pg_catalog", "pg_tables", "pg_user", "pg_shadow"
    );

    // 严禁调用的高危系统函数
    private static final List<String> DANGEROUS_FUNCTIONS = List.of(
            "pg_read_file", "pg_write_file", "pg_sleep", "pg_terminate_backend",
            "dblink", "query_to_xml", "version", "current_setting"
    );

    @Override
    public String getNodeType() {
        return "TEXT2SQL";
    }

    @Override
    public Mono<NodeExecutionResult> execute(WorkflowContext context, DagModel.DagNode node) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();

            Map<String, Object> nodeData = node.getData() != null ? node.getData() : new HashMap<>();
            String queryTpl = MapUtil.getStr(nodeData, "query", "{{query}}");
            String tableSchema = MapUtil.getStr(nodeData, "schema",
                    "表: sales_record (id BIGINT, region VARCHAR, sales_amount DECIMAL, quarter VARCHAR, create_time TIMESTAMP)");

            String resolvedQuery = context.resolveTemplate(queryTpl);

            // 1. 调用 LLM 生成只读 SQL
            String systemPrompt = String.format("""
                    你是一个资深数据库分析专家。根据以下数据表元数据生成标准 PostgreSQL 只读 SELECT 查询语句：
                    【Schema】
                    %s
                    
                    【要求】
                    1. 只能输出纯净的 SQL 语句，不要包含任何 markdown 代码块或解释。
                    2. 严禁生成任何更新、删除或 DDL 语句，只允许单条 SELECT 查询。
                    """, tableSchema);

            LlmResponse response = llmClient.generate(LlmRequest.builder()
                    .systemPrompt(systemPrompt)
                    .userPrompt(resolvedQuery)
                    .temperature(0.1)
                    .build());

            String rawSql = response.getContent().replaceAll("```sql|```", "").trim();

            // 2. 基于 JSqlParser AST 的硬核安全静态审计
            String safeSql;
            try {
                safeSql = validateAndHardenSql(rawSql);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("🛑 [TEXT2SQL_SECURITY_BLOCK] SQL 安全审计未通过: rawSql={}, error={}", rawSql, e.getMessage());
                return NodeExecutionResult.fail(node.getId(), getNodeType(), "SQL 安全审计拦截: " + e.getMessage(), duration);
            }

            // 3. 真实执行查询 (带 5 秒超时保护，真实报错绝不伪造)
            List<Map<String, Object>> queryRows;
            try {
                jdbcTemplate.setQueryTimeout(5);
                queryRows = jdbcTemplate.queryForList(safeSql);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("❌ [TEXT2SQL_EXEC_ERROR] SQL 数据库执行报错: sql={}, error={}", safeSql, e.getMessage());
                return NodeExecutionResult.fail(node.getId(), getNodeType(), "SQL 真实执行报错: " + e.getMessage(), duration);
            }

            // 4. 写入上下文变量
            context.setVariable(node.getId() + ".sql", safeSql);
            context.setVariable(node.getId() + ".data", queryRows);
            context.setVariable("last_sql_result", queryRows);

            Map<String, Object> outputs = new HashMap<>();
            outputs.put("generatedSql", safeSql);
            outputs.put("rowCount", queryRows.size());
            outputs.put("data", queryRows);

            long duration = System.currentTimeMillis() - startTime;
            return NodeExecutionResult.success(node.getId(), getNodeType(), outputs, duration);
        });
    }

    /**
     * 基于 JSqlParser 的 AST 深度解析与安全加固
     */
    public String validateAndHardenSql(String sql) throws Exception {
        if (StrUtil.isBlank(sql)) {
            throw new IllegalArgumentException("SQL 语句不能为空");
        }

        // 禁止分号多语句注入
        if (sql.contains(";") && !sql.endsWith(";")) {
            throw new SecurityException("Text2SQL 拦截：严禁多语句联合执行");
        }
        String cleanSql = sql.endsWith(";") ? sql.substring(0, sql.length() - 1).trim() : sql.trim();

        // 1. 语法树解析
        Statement statement = CCJSqlParserUtil.parse(cleanSql);
        if (!(statement instanceof Select select)) {
            throw new SecurityException("Text2SQL 拦截：仅允许执行只读 SELECT 查询");
        }

        // 2. 敏感表校验
        TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
        List<String> tableNames = tablesNamesFinder.getTableList(statement);
        for (String table : tableNames) {
            String lowerTable = table.toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN_TABLES) {
                if (lowerTable.equals(forbidden) || lowerTable.contains(forbidden)) {
                    throw new SecurityException("Text2SQL 拦截：严禁访问系统敏感表或元数据表 [" + table + "]");
                }
            }
        }

        // 3. 高危函数阻断
        String upperSql = cleanSql.toUpperCase(Locale.ROOT);
        for (String func : DANGEROUS_FUNCTIONS) {
            if (upperSql.contains(func.toUpperCase(Locale.ROOT))) {
                throw new SecurityException("Text2SQL 拦截：检测到高危函数调用 [" + func + "]");
            }
        }

        // 4. LIMIT 保护：防止未分页大查询拖垮内存 (默认最大 100 行)
        if (!upperSql.contains("LIMIT")) {
            cleanSql = cleanSql + " LIMIT 100";
        }

        return cleanSql;
    }
}

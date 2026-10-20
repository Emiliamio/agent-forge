package com.agentforge.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 演示与初始种子数据注入器 (DataSeeder)
 * 仅在空库 (sys_tenant 记录数为 0) 且配置 agentforge.seed.enabled=true 时执行 classpath:sql/seed-data.sql
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agentforge.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer tenantCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_tenant",
                Integer.class
        );

        if (tenantCount == null || tenantCount == 0) {
            log.info("🌱 [DataSeeder] 检测到全新空库环境，开始注入演示租户与默认管理员种子数据...");
            ClassPathResource seedRes = new ClassPathResource("sql/seed-data.sql");
            if (!seedRes.exists()) {
                throw new IllegalStateException("种子数据脚本 classpath:sql/seed-data.sql 未找到");
            }
            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource == null) {
                throw new IllegalStateException("JdbcTemplate DataSource 为空，无法注入种子数据");
            }
            try (Connection conn = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(conn, seedRes);
                log.info("✅ [DataSeeder] 种子初始数据注入完成！");
            } catch (Exception e) {
                log.error("❌ [DataSeeder] 种子初始数据注入失败: {}", e.getMessage(), e);
                throw new IllegalStateException("DataSeeder 种子初始数据注入失败: " + e.getMessage(), e);
            }
        } else {
            log.info("✨ [DataSeeder] 数据库已存在业务租户数据 (count={})，跳过种子初始化", tenantCount);
        }
    }
}

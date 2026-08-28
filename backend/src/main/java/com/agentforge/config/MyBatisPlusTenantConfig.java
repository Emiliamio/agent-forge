package com.agentforge.config;

import com.agentforge.context.TenantContextHolder;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MyBatis-Plus 租户与分页插件配置
 * 基于 JsqlParser 语法分析在 SQL 执行前自动注入 WHERE tenant_id = ?
 */
@Configuration
public class MyBatisPlusTenantConfig {

    /**
     * 忽略租户隔离的全局系统表清单
     */
    private static final List<String> IGNORE_TENANT_TABLES = List.of(
            "sys_tenant"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 多租户插件 (必须置于分页插件之前)
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                // 动态获取当前线程绑定的租户 ID
                return new LongValue(TenantContextHolder.getTenantId());
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 忽略全局表 (如租户元数据表)
                return IGNORE_TENANT_TABLES.stream().anyMatch(t -> t.equalsIgnoreCase(tableName));
            }
        }));

        // 2. 分页插件 (PostgreSQL 方言)
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));

        return interceptor;
    }
}

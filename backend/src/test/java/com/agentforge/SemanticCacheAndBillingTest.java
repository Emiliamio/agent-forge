package com.agentforge;

import com.agentforge.service.rag.acl.AclPermissionContext;
import com.agentforge.service.rag.parser.StructuredTableChunker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

@DisplayName("Phase 4 & 进阶战役：语义降本缓存、计量计费与结构化表格切片测试")
public class SemanticCacheAndBillingTest {

    @Test
    @DisplayName("测试结构化表格级联表头下沉切片算法 (防止跨页表头脱节)")
    void testStructuredTableChunker() {
        List<String> headers = List.of("部门", "员工姓名", "季度", "销售业绩(万)");
        List<List<String>> rows = List.of(
                List.of("华东销售一区", "张三", "2025Q3", "188.50"),
                List.of("华东销售一区", "李四", "2025Q3", "240.00")
        );

        List<String> chunks = StructuredTableChunker.chunkTableWithHierarchy(headers, rows);

        Assertions.assertEquals(2, chunks.size());
        Assertions.assertTrue(chunks.get(0).contains("[部门: 华东销售一区]"));
        Assertions.assertTrue(chunks.get(0).contains("[员工姓名: 张三]"));
        Assertions.assertTrue(chunks.get(0).contains("[销售业绩(万): 188.50]"));
        Assertions.assertTrue(chunks.get(1).contains("[员工姓名: 李四]"));
    }

    @Test
    @DisplayName("测试 ACL 权限模型构建与超级管理员免过滤标识")
    void testAclPermissionContext() {
        AclPermissionContext adminContext = AclPermissionContext.builder()
                .userId(1L)
                .isSuperAdmin(true)
                .build();

        Assertions.assertTrue(adminContext.isSuperAdmin());

        AclPermissionContext userContext = AclPermissionContext.builder()
                .userId(1001L)
                .deptId(200L)
                .roleIds(List.of(10L, 20L))
                .isSuperAdmin(false)
                .build();

        Assertions.assertFalse(userContext.isSuperAdmin());
        Assertions.assertEquals(200L, userContext.getDeptId());
        Assertions.assertEquals(2, userContext.getRoleIds().size());
    }
}

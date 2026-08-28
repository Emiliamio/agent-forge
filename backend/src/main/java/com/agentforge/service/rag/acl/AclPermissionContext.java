package com.agentforge.service.rag.acl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 权限上下文 (ACL Context)
 * 用于在 RAG 稠密向量检索与 BM25 全文检索中进行物理联合权限过滤
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclPermissionContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long deptId;
    private List<Long> roleIds;
    private List<String> permissions;

    /**
     * 是否为超级租户管理员 (跳过 ACL 权限过滤)
     */
    private boolean isSuperAdmin;
}

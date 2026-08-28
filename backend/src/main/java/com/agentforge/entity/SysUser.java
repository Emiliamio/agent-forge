package com.agentforge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 系统用户实体 (支持多租户与 RBAC)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private Long tenantId;

    private String username;

    @JsonIgnore
    private String passwordHash;

    @JsonIgnore
    private String salt;

    private String realName;

    private String email;

    private String role; // OWNER, ADMIN, EDITOR, VIEWER

    private Integer status; // 1: 正常, 0: 禁用
}

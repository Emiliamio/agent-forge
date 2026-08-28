package com.agentforge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工作流 DAG 拓扑定义实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_definition")
public class WorkflowDefinition extends BaseEntity {

    private Long tenantId;

    private Long appId;

    private String name;

    private String description;

    /**
     * DAG 拓扑定义 (JSON 格式：包含 nodes 与 edges)
     */
    private String dagJson;

    private Integer version;

    private Integer status; // 1: 启用, 0: 禁用
}

package com.agentforge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工作流执行实例表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_execution")
public class WorkflowExecution extends BaseEntity {

    private Long tenantId;

    private Long workflowId;

    private Long appId;

    private String triggerType; // API, STUDIO, WEBHOOK, SCHEDULE

    private String status; // RUNNING, SUCCESS, FAILED, TIMEOUT

    private String inputSnapshot; // JSONB

    private String outputSnapshot; // JSONB

    private String stepDetails; // JSONB

    private Long totalDurationMs;

    private String errorMsg;
}

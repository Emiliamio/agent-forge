package com.agentforge.service.workflow.node;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 单节点执行输出结果载体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExecutionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String nodeId;
    private String nodeType;
    private String status; // SUCCESS, FAILED, SKIPPED
    private Map<String, Object> outputData;

    /**
     * 条件分支节点选中的下游连线 Handle (如 "true" / "false")
     */
    private String selectedNextHandle;

    private long durationMs;
    private String errorMsg;

    public static NodeExecutionResult success(String nodeId, String nodeType, Map<String, Object> outputData, long durationMs) {
        return NodeExecutionResult.builder()
                .nodeId(nodeId)
                .nodeType(nodeType)
                .status("SUCCESS")
                .outputData(outputData)
                .durationMs(durationMs)
                .build();
    }

    public static NodeExecutionResult fail(String nodeId, String nodeType, String errorMsg, long durationMs) {
        return NodeExecutionResult.builder()
                .nodeId(nodeId)
                .nodeType(nodeType)
                .status("FAILED")
                .errorMsg(errorMsg)
                .durationMs(durationMs)
                .build();
    }
}

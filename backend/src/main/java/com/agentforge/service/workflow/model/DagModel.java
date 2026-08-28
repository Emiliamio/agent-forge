package com.agentforge.service.workflow.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * DAG 工作流拓扑定义模型 (兼容 @vue-flow 画布数据结构)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DAG 工作流拓扑模型")
public class DagModel implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "工作流节点列表")
    private List<DagNode> nodes;

    @Schema(description = "工作流连线列表")
    private List<DagEdge> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "DAG 节点定义")
    public static class DagNode implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "节点唯一 ID", example = "node_1")
        private String id;

        @Schema(description = "节点类型 (START, LLM, KNOWLEDGE, CONDITION, HTTP, CODE, END)", example = "LLM")
        private String type;

        @Schema(description = "节点名称", example = "技术方案生成模型")
        private String name;

        @Schema(description = "节点特有配置参数 (Prompt, 模型, URL, 分支条件等)")
        private Map<String, Object> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "DAG 连线定义")
    public static class DagEdge implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "连线唯一 ID", example = "e1-2")
        private String id;

        @Schema(description = "起始节点 ID", example = "node_1")
        private String source;

        @Schema(description = "目标节点 ID", example = "node_2")
        private String target;

        @Schema(description = "起始手柄标识 (如 condition 节点的 true / false 分支)", example = "true")
        private String sourceHandle;

        @Schema(description = "分支条件表达式 (可选)")
        private String condition;
    }
}

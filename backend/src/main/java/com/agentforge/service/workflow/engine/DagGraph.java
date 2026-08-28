package com.agentforge.service.workflow.engine;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.service.workflow.model.DagModel;
import lombok.Getter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * DAG 有向无环图结构解析与拓扑排序调度器
 * 基于 Kahn 算法实现分层并行拓扑排序，并集成环路死锁检测
 */
@Getter
public class DagGraph {

    private final DagModel model;
    private final Map<String, DagModel.DagNode> nodeMap = new HashMap<>();
    private final Map<String, List<DagModel.DagEdge>> outEdges = new HashMap<>();
    private final Map<String, List<DagModel.DagEdge>> inEdges = new HashMap<>();
    private final Map<String, Integer> inDegreeMap = new HashMap<>();
    private DagModel.DagNode startNode;

    public DagGraph(DagModel model) {
        this.model = model;
        initGraph();
    }

    private void initGraph() {
        if (model == null || model.getNodes() == null || model.getNodes().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "工作流至少需要包含一个开始节点");
        }

        // 1. 初始化节点映射与入度表
        for (DagModel.DagNode node : model.getNodes()) {
            nodeMap.put(node.getId(), node);
            outEdges.put(node.getId(), new ArrayList<>());
            inEdges.put(node.getId(), new ArrayList<>());
            inDegreeMap.put(node.getId(), 0);

            if ("START".equalsIgnoreCase(node.getType())) {
                this.startNode = node;
            }
        }

        if (startNode == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "工作流缺失 START 开始节点");
        }

        // 2. 初始化连线邻接表与入度计算
        if (model.getEdges() != null) {
            for (DagModel.DagEdge edge : model.getEdges()) {
                if (!nodeMap.containsKey(edge.getSource()) || !nodeMap.containsKey(edge.getTarget())) {
                    continue;
                }
                outEdges.get(edge.getSource()).add(edge);
                inEdges.get(edge.getTarget()).add(edge);
                inDegreeMap.put(edge.getTarget(), inDegreeMap.get(edge.getTarget()) + 1);
            }
        }
    }

    /**
     * 基于 Kahn 算法计算拓扑执行分层 (Topological Stages)
     * 每一层中的节点相互无依赖，可由 Project Reactor 响应式并行并发执行
     *
     * @return 分层拓扑节点列表
     */
    public List<List<DagModel.DagNode>> computeTopologicalTiers() {
        Map<String, Integer> currentInDegree = new HashMap<>(inDegreeMap);
        Queue<String> zeroInDegreeQueue = new ArrayDeque<>();
        List<List<DagModel.DagNode>> tiers = new ArrayList<>();
        int visitedNodesCount = 0;

        // 寻找初始入度为 0 的节点 (如 START 节点)
        for (Map.Entry<String, Integer> entry : currentInDegree.entrySet()) {
            if (entry.getValue() == 0) {
                zeroInDegreeQueue.offer(entry.getKey());
            }
        }

        while (!zeroInDegreeQueue.isEmpty()) {
            int currentTierSize = zeroInDegreeQueue.size();
            List<DagModel.DagNode> currentTierNodes = new ArrayList<>(currentTierSize);
            List<String> nextZeroInDegreeCandidates = new ArrayList<>();

            for (int i = 0; i < currentTierSize; i++) {
                String nodeId = zeroInDegreeQueue.poll();
                visitedNodesCount++;
                currentTierNodes.add(nodeMap.get(nodeId));

                // 遍历当前节点的所有出边，将其目标节点的入度减 1
                for (DagModel.DagEdge edge : outEdges.getOrDefault(nodeId, Collections.emptyList())) {
                    String targetId = edge.getTarget();
                    int newInDegree = currentInDegree.get(targetId) - 1;
                    currentInDegree.put(targetId, newInDegree);
                    if (newInDegree == 0) {
                        nextZeroInDegreeCandidates.add(targetId);
                    }
                }
            }

            tiers.add(currentTierNodes);
            zeroInDegreeQueue.addAll(nextZeroInDegreeCandidates);
        }

        // 环路死锁检测：如果遍历过的节点数少于总节点数，说明图中存在环 (Cycle)
        if (visitedNodesCount < nodeMap.size()) {
            throw new BusinessException(ErrorCode.WORKFLOW_CYCLE_DETECTED,
                    String.format("工作流校验失败：检测到拓扑存在环路依赖或死锁 (已访问 %d/%d 节点)", visitedNodesCount, nodeMap.size()));
        }

        return tiers;
    }

    /**
     * 校验 DAG 是否包含有效路径
     */
    public void validate() {
        computeTopologicalTiers();
    }
}

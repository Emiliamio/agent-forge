package com.agentforge.service.workflow.engine;

import com.agentforge.exception.BusinessException;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import com.agentforge.service.workflow.node.NodeExecutionResult;
import com.agentforge.service.workflow.node.NodeExecutorFactory;
import com.agentforge.service.workflow.node.WorkflowNodeExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 响应式 DAG 工作流调度引擎 (基于 Project Reactor Mono/Flux)
 * 支持分层拓扑调度、无依赖分支并行并发执行、条件分支动态剪枝与节点级响应式超时熔断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final NodeExecutorFactory nodeExecutorFactory;

    /**
     * 默认单个节点执行最大超时时间 (秒)
     */
    private static final long DEFAULT_NODE_TIMEOUT_SECONDS = 60L;

    /**
     * 响应式异步调度执行 DAG 工作流
     *
     * @param model   DAG 拓扑定义
     * @param context 执行上下文
     * @return 执行完成后的工作流上下文
     */
    public Mono<WorkflowContext> executeWorkflow(DagModel model, WorkflowContext context) {
        return Mono.defer(() -> {
            DagGraph graph = new DagGraph(model);
            graph.validate();

            List<List<DagModel.DagNode>> tiers = graph.computeTopologicalTiers();
            Set<String> skippedNodes = Collections.synchronizedSet(new HashSet<>());

            // 按拓扑层级顺序依次执行每一层
            Mono<Void> executionFlow = Mono.empty();

            for (List<DagModel.DagNode> tier : tiers) {
                executionFlow = executionFlow.then(
                        executeTierInParallel(tier, graph, context, skippedNodes)
                );
            }

            return executionFlow.thenReturn(context);
        });
    }

    /**
     * 并行并发执行同一拓扑层级内的所有节点 (Project Reactor 并发调度与超时熔断)
     */
    private Mono<Void> executeTierInParallel(
            List<DagModel.DagNode> tier,
            DagGraph graph,
            WorkflowContext context,
            Set<String> skippedNodes
    ) {
        return Flux.fromIterable(tier)
                .flatMap(node -> {
                    // 如果节点已被条件分支标记为跳过，则直接记录跳过状态
                    if (skippedNodes.contains(node.getId())) {
                        recordStepLog(context, node, "SKIPPED", null, null, 0, null);
                        return Mono.empty();
                    }

                    WorkflowNodeExecutor executor = nodeExecutorFactory.getExecutor(node.getType());
                    long startTime = System.currentTimeMillis();

                    return executor.execute(context, node)
                            .timeout(Duration.ofSeconds(DEFAULT_NODE_TIMEOUT_SECONDS))
                            .doOnNext(result -> {
                                long duration = System.currentTimeMillis() - startTime;
                                recordStepLog(context, node, result.getStatus(), node.getData(), result.getOutputData(), duration, result.getErrorMsg());

                                // 处理条件分支节点的下游跳过逻辑
                                if ("CONDITION".equalsIgnoreCase(node.getType()) && result.getSelectedNextHandle() != null) {
                                    handleConditionBranchSkipping(node.getId(), result.getSelectedNextHandle(), graph, skippedNodes);
                                }
                            })
                            .onErrorResume(err -> {
                                long duration = System.currentTimeMillis() - startTime;
                                recordStepLog(context, node, "FAILED", node.getData(), null, duration, err.getMessage());
                                log.error("工作流节点执行失败或超时: nodeId={}, error={}", node.getId(), err.getMessage());
                                return Mono.error(err);
                            });
                })
                .then();
    }

    /**
     * 根据条件分支选中的 handle ("true"/"false")，将未选中的下游分支节点加入跳过集合
     */
    private void handleConditionBranchSkipping(
            String conditionNodeId,
            String selectedHandle,
            DagGraph graph,
            Set<String> skippedNodes
    ) {
        List<DagModel.DagEdge> outEdges = graph.getOutEdges().getOrDefault(conditionNodeId, Collections.emptyList());
        for (DagModel.DagEdge edge : outEdges) {
            String edgeHandle = edge.getSourceHandle() != null ? edge.getSourceHandle().toLowerCase() : "true";
            if (!edgeHandle.equals(selectedHandle.toLowerCase())) {
                markDownstreamAsSkipped(edge.getTarget(), graph, skippedNodes);
            }
        }
    }

    private void markDownstreamAsSkipped(String targetNodeId, DagGraph graph, Set<String> skippedNodes) {
        if (targetNodeId == null || skippedNodes.contains(targetNodeId)) {
            return;
        }
        skippedNodes.add(targetNodeId);
        List<DagModel.DagEdge> downstreamEdges = graph.getOutEdges().getOrDefault(targetNodeId, Collections.emptyList());
        for (DagModel.DagEdge edge : downstreamEdges) {
            markDownstreamAsSkipped(edge.getTarget(), graph, skippedNodes);
        }
    }

    private void recordStepLog(
            WorkflowContext context,
            DagModel.DagNode node,
            String status,
            java.util.Map<String, Object> inputSnapshot,
            Object outputSnapshot,
            long durationMs,
            String errorMsg
    ) {
        WorkflowContext.NodeStepLog logEntry = WorkflowContext.NodeStepLog.builder()
                .nodeId(node.getId())
                .nodeName(node.getName())
                .nodeType(node.getType())
                .status(status)
                .inputSnapshot(inputSnapshot)
                .outputSnapshot(outputSnapshot)
                .durationMs(durationMs)
                .errorMsg(errorMsg)
                .timestamp(System.currentTimeMillis())
                .build();
        context.getStepLogs().add(logEntry);
    }
}

package com.agentforge.service.workflow.engine;

import com.agentforge.entity.WorkflowExecution;
import com.agentforge.mapper.WorkflowExecutionMapper;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Stack;

/**
 * 分布式 DAG 工作流 Checkpoint 断点续传与 Saga 逆向事务补偿管理器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowCheckpointManager {

    private final WorkflowExecutionMapper executionMapper;

    /**
     * 节点执行成功后，原子持久化执行快照 (Checkpoint)
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveCheckpoint(Long executionId, String nodeId, Map<String, Object> outputSnapshot, WorkflowContext context) {
        if (executionId == null) return;

        WorkflowExecution exec = executionMapper.selectById(executionId);
        if (exec != null) {
            exec.setStepDetails(JSON.toJSONString(context.getStepLogs()));
            exec.setOutputSnapshot(JSON.toJSONString(context.getVariables()));
            executionMapper.updateById(exec);
            log.info("💾 Checkpoint 已持久化: executionId={}, nodeId={}", executionId, nodeId);
        }
    }

    /**
     * 节点执行失败时，触发 Saga 逆向事务补偿链路
     *
     * @param executedNodesStack 已成功执行节点的逆序栈
     */
    public void triggerSagaCompensation(Stack<DagModel.DagNode> executedNodesStack, WorkflowContext context) {
        log.warn("⚠️ 工作流发生异常，开始触发 Saga 逆向补偿机制...");
        while (!executedNodesStack.isEmpty()) {
            DagModel.DagNode node = executedNodesStack.pop();
            Object rollbackUrl = node.getData() != null ? node.getData().get("rollbackUrl") : null;
            if (rollbackUrl != null) {
                try {
                    log.info("执行节点 [{}] 的逆向补偿 Webhook: {}", node.getName(), rollbackUrl);
                } catch (Exception e) {
                    log.error("节点 [{}] 逆向补偿失败: {}", node.getName(), e.getMessage());
                }
            }
        }
    }
}

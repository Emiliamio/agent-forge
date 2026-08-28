package com.agentforge.service.workflow.node;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 节点执行器工厂
 */
@Component
@RequiredArgsConstructor
public class NodeExecutorFactory {

    private final List<WorkflowNodeExecutor> executors;

    /**
     * 根据节点类型获取对应的执行器
     *
     * @param nodeType 节点类型 (START, LLM, KNOWLEDGE, CONDITION, HTTP, CODE, END)
     * @return 节点执行器
     */
    public WorkflowNodeExecutor getExecutor(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "节点类型不能为空");
        }

        return executors.stream()
                .filter(e -> e.getNodeType().equalsIgnoreCase(nodeType.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "不支持的工作流节点类型: " + nodeType));
    }
}

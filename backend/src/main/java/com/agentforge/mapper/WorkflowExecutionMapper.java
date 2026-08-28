package com.agentforge.mapper;

import com.agentforge.entity.WorkflowExecution;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流执行记录 Mapper 接口
 */
@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecution> {
}

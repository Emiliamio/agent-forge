package com.agentforge.mapper;

import com.agentforge.entity.TokenUsageLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Token 计量审计 Mapper 接口
 */
@Mapper
public interface TokenUsageLogMapper extends BaseMapper<TokenUsageLog> {
}

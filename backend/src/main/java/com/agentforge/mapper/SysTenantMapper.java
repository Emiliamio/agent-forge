package com.agentforge.mapper;

import com.agentforge.entity.SysTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 Mapper 接口
 */
@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {
}

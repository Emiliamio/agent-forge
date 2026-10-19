package com.agentforge.mapper;

import com.agentforge.entity.AgentApp;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 智能体应用 Mapper 接口
 */
@Mapper
public interface AgentAppMapper extends BaseMapper<AgentApp> {

    /**
     * 忽略租户物理隔离拦截器，跨租户查询智能体元数据 (仅供公开免密挂件校验使用)
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM agent_app WHERE id = #{id}")
    AgentApp selectByIdWithoutTenant(@Param("id") Long id);
}

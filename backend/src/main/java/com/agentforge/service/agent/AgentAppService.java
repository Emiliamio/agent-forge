package com.agentforge.service.agent;

import com.agentforge.entity.AgentApp;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 智能体应用核心数据访问与门面服务
 */
public interface AgentAppService {

    AgentApp createAgent(AgentApp app, Long tenantId);

    Page<AgentApp> listAgents(Long tenantId, String keyword, long pageNum, long pageSize);

    AgentApp getAgent(Long id, Long tenantId);

    AgentApp findPublicApp(Long id);
}

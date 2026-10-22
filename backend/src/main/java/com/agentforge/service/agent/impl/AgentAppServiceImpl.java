package com.agentforge.service.agent.impl;

import cn.hutool.core.util.StrUtil;
import com.agentforge.entity.AgentApp;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.AgentAppMapper;
import com.agentforge.service.agent.AgentAppService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 智能体应用核心数据访问与门面服务实现
 */
@Service
@RequiredArgsConstructor
public class AgentAppServiceImpl implements AgentAppService {

    private final AgentAppMapper agentAppMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentApp createAgent(AgentApp app, Long tenantId) {
        app.setTenantId(tenantId);
        app.setStatus(1);
        if (StrUtil.isBlank(app.getAppType())) {
            app.setAppType("AGENT");
        }
        agentAppMapper.insert(app);
        return app;
    }

    @Override
    public Page<AgentApp> listAgents(Long tenantId, String keyword, long pageNum, long pageSize) {
        LambdaQueryWrapper<AgentApp> wrapper = new LambdaQueryWrapper<AgentApp>()
                .eq(AgentApp::getTenantId, tenantId)
                .eq(AgentApp::getStatus, 1)
                .like(StrUtil.isNotBlank(keyword), AgentApp::getName, keyword)
                .orderByDesc(AgentApp::getId);
        return agentAppMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public AgentApp getAgent(Long id, Long tenantId) {
        AgentApp app = agentAppMapper.selectById(id);
        if (app == null || !app.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.AGENT_APP_NOT_FOUND);
        }
        return app;
    }

    @Override
    public AgentApp findPublicApp(Long id) {
        return agentAppMapper.selectByIdWithoutTenant(id);
    }
}

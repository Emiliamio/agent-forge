package com.agentforge.service.market.api;

import com.agentforge.entity.WorkflowDefinition;
import com.agentforge.vo.IndustryTemplateVo;
import com.agentforge.vo.WorkflowDefinitionVo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 企业级 6 大高客单价垂直行业应用与工作流模板市场服务接口
 */
public interface TemplateMarketService {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class IndustryTemplate implements Serializable {
        private static final long serialVersionUID = 1L;

        private String templateId;
        private String name;
        private String category; // 金融财务, 招投标, IT运维, 法务合同, 政务政策, 智能客服
        private String icon;
        private String description;
        private String valueProposition; // 商业交付价值说明 (如: 节省 80% 人工初审时间)
        private String estimatedPrice;    // 市场常规交付参考客单价 (如: ¥50,000 ~ ¥100,000)
        private String dagJson;
        private String defaultPrompt;
    }

    /**
     * 获取模板市场全量模板列表
     */
    List<IndustryTemplate> listTemplates();

    /**
     * 一键克隆模板到当前租户的工作流与智能体列表
     */
    WorkflowDefinition cloneTemplateToTenant(String templateId);

    /**
     * 获取模板市场全量模板 VO 列表
     */
    List<IndustryTemplateVo> listTemplateVos();

    /**
     * 一键克隆模板并返回 WorkflowDefinitionVo
     */
    WorkflowDefinitionVo cloneTemplateToTenantVo(String templateId);
}

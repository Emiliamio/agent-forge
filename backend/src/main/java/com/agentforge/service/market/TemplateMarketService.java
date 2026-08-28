package com.agentforge.service.market;

import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.AgentApp;
import com.agentforge.entity.WorkflowDefinition;
import com.agentforge.mapper.AgentAppMapper;
import com.agentforge.mapper.WorkflowDefinitionMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * 企业级 6 大高客单价垂直行业应用与工作流模板市场服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateMarketService {

    private final WorkflowDefinitionMapper workflowMapper;
    private final AgentAppMapper appMapper;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndustryTemplate implements Serializable {
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

    private static final List<IndustryTemplate> TEMPLATES = List.of(
            IndustryTemplate.builder()
                    .templateId("tpl_financial_audit")
                    .name("上市公司财务与年报对比审计工作流")
                    .category("金融财务")
                    .icon("📈")
                    .description("自动比对近三年财务报表中的核心资产负债、营收利润与现金流变动率，识别财务造假与异常波动风险。")
                    .valueProposition("将 300 页财报比对耗时从 3 天缩短至 15 秒，自动生成杜邦分析法穿透报告。")
                    .estimatedPrice("¥ 80,000 ~ ¥ 150,000")
                    .defaultPrompt("你是一个资深注册会计师 (CPA)。请严格对标两期资产负债表与利润表，指出营收增长与现金流不匹配的潜在审计风险。")
                    .dagJson("{\"nodes\":[{\"id\":\"start\",\"type\":\"START\"},{\"id\":\"t2sql\",\"type\":\"TEXT2SQL\"},{\"id\":\"end\",\"type\":\"END\"}]}")
                    .build(),

            IndustryTemplate.builder()
                    .templateId("tpl_tender_compliance")
                    .name("大型招投标废标项实时自检工作流")
                    .category("招投标")
                    .icon("📋")
                    .description("将应标书与招标文件的商务、技术、资质要求逐条比对，自动标红响应不合规项与废标高危条款。")
                    .valueProposition("彻底消除因格式、工期承诺或漏项导致的百万元级废标风险。")
                    .estimatedPrice("¥ 60,000 ~ ¥ 120,000")
                    .defaultPrompt("你是一个招投标合规审查专家。请标红应标书中响应时间、付款周期与招标文件不符的高危项。")
                    .dagJson("{\"nodes\":[{\"id\":\"start\",\"type\":\"START\"},{\"id\":\"comp\",\"type\":\"COMPLIANCE\"},{\"id\":\"end\",\"type\":\"END\"}]}")
                    .build(),

            IndustryTemplate.builder()
                    .templateId("tpl_it_ops_diagnosis")
                    .name("企业 IT 运维故障根因排查与工单流转")
                    .category("IT运维")
                    .icon("⚡")
                    .description("自动抓取微服务集群日志与监控告警指标，快速匹配知识库历史故障排查手册，并派发工单。")
                    .valueProposition("MTTR (平均故障修复时间) 降低 65%，杜绝 P1 级线上雪崩事故。")
                    .estimatedPrice("¥ 50,000 ~ ¥ 100,000")
                    .defaultPrompt("你是一个 SRE 架构师。根据给定的堆栈日志定位死锁或 OOM 根因，并给出紧急止血指令。")
                    .dagJson("{\"nodes\":[{\"id\":\"start\",\"type\":\"START\"},{\"id\":\"http\",\"type\":\"HTTP_REQUEST\"},{\"id\":\"end\",\"type\":\"END\"}]}")
                    .build(),

            IndustryTemplate.builder()
                    .templateId("tpl_legal_claim_calc")
                    .name("保险与法务合同违约索赔核算模板")
                    .category("法务合同")
                    .icon("⚖️")
                    .description("依据主合同违约责任条款，自动抓取违约起止天数，调用内置精准计算器核算逾期滞纳金与违约金。")
                    .valueProposition("法律纠纷金额核算零差错，直接生成可作为诉讼证据的违约索赔明细清单。")
                    .estimatedPrice("¥ 50,000 ~ ¥ 80,000")
                    .defaultPrompt("你是一个法务律师。请依据合同第 14 条逾期违约金比例计算具体滞纳金。")
                    .dagJson("{\"nodes\":[{\"id\":\"start\",\"type\":\"START\"},{\"id\":\"llm\",\"type\":\"LLM\"},{\"id\":\"end\",\"type\":\"END\"}]}")
                    .build(),

            IndustryTemplate.builder()
                    .templateId("tpl_policy_self_check")
                    .name("政务公开与科技政策申报自检模板")
                    .category("政务政策")
                    .icon("🏛️")
                    .description("企业上传专利数、研发占比与员工学历后，自动与发改委、科技局申报标准打分匹配。")
                    .valueProposition("提高政府科技项目申报成功率 40%，自动输出申报名册补全清单。")
                    .estimatedPrice("¥ 40,000 ~ ¥ 80,000")
                    .defaultPrompt("你是一个科技项目申报专家。请评估企业研发费用占比是否达到高新技术企业 5% 的硬性指标。")
                    .dagJson("{\"nodes\":[{\"id\":\"start\",\"type\":\"START\"},{\"id\":\"code\",\"type\":\"CODE\"},{\"id\":\"end\",\"type\":\"END\"}]}")
                    .build(),

            IndustryTemplate.builder()
                    .templateId("tpl_customer_service")
                    .name("全渠道电商智能客服与情绪分流模板")
                    .category("智能客服")
                    .icon("🎧")
                    .description("毫秒级识别买家售后诉求与情绪负面等级，结合商品知识库提供自动退换货引导与高危客诉人工转接。")
                    .valueProposition("客服人力成本下降 50%，高危客诉 3 秒内升级阻断。")
                    .estimatedPrice("¥ 30,000 ~ ¥ 60,000")
                    .defaultPrompt("你是一个资深客服专家。遇到愤怒客户优先共情安抚，并给出合规退换货方案。")
                    .dagJson("{\"nodes\":[{\"id\":\"start\",\"type\":\"START\"},{\"id\":\"rag\",\"type\":\"KNOWLEDGE_RETRIEVAL\"},{\"id\":\"end\",\"type\":\"END\"}]}")
                    .build()
    );

    /**
     * 获取模板市场全量模板列表
     */
    public List<IndustryTemplate> listTemplates() {
        return TEMPLATES;
    }

    /**
     * 一键克隆模板到当前租户的工作流与智能体列表
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinition cloneTemplateToTenant(String templateId) {
        Long tenantId = TenantContextHolder.getTenantId();

        IndustryTemplate tpl = TEMPLATES.stream()
                .filter(t -> t.getTemplateId().equals(templateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateId));

        // 1. 创建工作流定义
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .tenantId(tenantId)
                .name("【行业模板】" + tpl.getName())
                .description(tpl.getDescription())
                .dagJson(tpl.getDagJson())
                .version(1)
                .status(1)
                .build();
        workflowMapper.insert(workflow);

        // 2. 创建关联智能体应用
        AgentApp app = AgentApp.builder()
                .tenantId(tenantId)
                .name(tpl.getName() + " Copilot")
                .description(tpl.getDescription())
                .appType("WORKFLOW")
                .modelConfig("{\"provider\":\"deepseek\",\"model\":\"deepseek-chat\"}")
                .systemPrompt(tpl.getDefaultPrompt())
                .status(1)
                .build();
        appMapper.insert(app);

        log.info("🎉 行业高客单价模板一键克隆成功: tenantId={}, tplId={}, workflowId={}, appId={}",
                tenantId, templateId, workflow.getId(), app.getId());

        return workflow;
    }
}

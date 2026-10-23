package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.agentforge.service.market.TemplateMarketService;
import com.agentforge.vo.IndustryTemplateVo;
import com.agentforge.vo.Result;
import com.agentforge.vo.WorkflowDefinitionVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 6 大高客单价垂直行业应用与工作流模板市场接口
 */
@Tag(name = "12. 垂直行业应用与工作流模板市场", description = "提供金融审计、招投标筛查、IT运维、法务索赔等开箱即用模板")
@RestController
@RequestMapping("/market")
@RequiredArgsConstructor
@SaCheckLogin
public class TemplateMarketController {

    private final TemplateMarketService marketService;

    @Operation(summary = "获取模板市场全量模板列表")
    @GetMapping("/templates")
    public Result<List<IndustryTemplateVo>> listTemplates() {
        return Result.success(marketService.listTemplateVos());
    }

    @Operation(summary = "一键克隆行业模板到当前租户")
    @PostMapping("/templates/{templateId}/clone")
    public Result<WorkflowDefinitionVo> cloneTemplate(@PathVariable String templateId) {
        WorkflowDefinitionVo workflow = marketService.cloneTemplateToTenantVo(templateId);
        return Result.success("模板克隆成功，已加入工作流列表", workflow);
    }
}

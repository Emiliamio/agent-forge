package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.util.StrUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.AgentApp;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.AgentAppMapper;
import com.agentforge.service.agent.ReActAgentService;
import com.agentforge.tools.BaseTool;
import com.agentforge.tools.ToolRegistry;
import com.agentforge.vo.PageResult;
import com.agentforge.vo.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体应用编排与 ReAct 对话调度接口
 */
@Tag(name = "07. 智能体编排与 ReAct 对话", description = "提供智能体创建、工具列表获取与 ReAct 多轮推理对话")
@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
@SaCheckLogin
public class AgentAppController {

    private final AgentAppMapper agentAppMapper;
    private final ReActAgentService reActAgentService;
    private final ToolRegistry toolRegistry;

    @Operation(summary = "创建智能体应用")
    @SaCheckRole(value = {"OWNER", "ADMIN", "EDITOR"}, mode = SaMode.OR)
    @PostMapping
    public Result<AgentApp> createAgent(@RequestBody AgentApp app) {
        app.setTenantId(TenantContextHolder.getTenantId());
        app.setStatus(1);
        if (StrUtil.isBlank(app.getAppType())) {
            app.setAppType("AGENT");
        }
        agentAppMapper.insert(app);
        return Result.success("智能体创建成功", app);
    }

    @Operation(summary = "分页查询当前租户智能体列表")
    @GetMapping
    public Result<PageResult<AgentApp>> listAgents(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        Long tenantId = TenantContextHolder.getTenantId();
        LambdaQueryWrapper<AgentApp> wrapper = new LambdaQueryWrapper<AgentApp>()
                .eq(AgentApp::getTenantId, tenantId)
                .eq(AgentApp::getStatus, 1)
                .like(StrUtil.isNotBlank(keyword), AgentApp::getName, keyword)
                .orderByDesc(AgentApp::getId);

        Page<AgentApp> page = agentAppMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return Result.success(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "获取智能体详情")
    @GetMapping("/{id}")
    public Result<AgentApp> getAgent(@PathVariable Long id) {
        AgentApp app = agentAppMapper.selectById(id);
        if (app == null || !app.getTenantId().equals(TenantContextHolder.getTenantId())) {
            throw new BusinessException(ErrorCode.AGENT_APP_NOT_FOUND);
        }
        return Result.success(app);
    }

    @Operation(summary = "与智能体进行 ReAct 推理对话 (包含工具调用步骤追踪与溯源引用)")
    @PostMapping("/{id}/chat")
    public Result<ReActAgentService.AgentExecutionResult> chat(
            @PathVariable Long id,
            @RequestBody ChatRequest request
    ) {
        int maxIterations = request.getMaxIterations() > 0 ? request.getMaxIterations() : 5;
        ReActAgentService.AgentExecutionResult result = reActAgentService.runAgent(id, request.getMessage(), maxIterations);
        return Result.success("对话完成", result);
    }

    @Operation(summary = "获取平台当前所有可用 Function Calling 工具清单")
    @GetMapping("/tools")
    public Result<List<Map<String, Object>>> listTools() {
        List<BaseTool> tools = toolRegistry.listAllTools();
        List<Map<String, Object>> result = tools.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", t.getName());
            map.put("description", t.getDescription());
            map.put("parameters", t.getParametersSchema());
            return map;
        }).toList();
        return Result.success(result);
    }

    @Data
    public static class ChatRequest {
        @NotBlank(message = "用户提问不能为空")
        private String message;
        private int maxIterations = 5;
    }
}

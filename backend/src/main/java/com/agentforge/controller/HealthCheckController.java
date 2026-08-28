package com.agentforge.controller;

import com.agentforge.service.system.SystemSelfCheckService;
import com.agentforge.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交付级系统启动自检与基础设施健康诊断接口
 */
@Tag(name = "13. 系统自检与基础设施健康诊断", description = "提供一键环境排查，涵盖数据库、Redis、磁盘与运行时")
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class HealthCheckController {

    private final SystemSelfCheckService selfCheckService;

    @Operation(summary = "执行全量基础设施一键自检诊断")
    @GetMapping("/diagnostics")
    public Result<SystemSelfCheckService.DiagnosticReport> getDiagnostics() {
        return Result.success(selfCheckService.runDiagnostics());
    }
}

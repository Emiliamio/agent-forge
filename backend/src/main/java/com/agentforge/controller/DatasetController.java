package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.agentforge.entity.Dataset;
import com.agentforge.service.rag.DatasetService;
import com.agentforge.vo.PageResult;
import com.agentforge.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库数据集管理接口
 */
@Tag(name = "03. 知识库数据集管理", description = "提供知识库数据集的创建、分页查询与级联删除")
@RestController
@RequestMapping("/datasets")
@RequiredArgsConstructor
@SaCheckLogin
public class DatasetController {

    private final DatasetService datasetService;

    @Operation(summary = "创建知识库数据集")
    @SaCheckRole(value = {"OWNER", "ADMIN", "EDITOR"}, mode = SaMode.OR)
    @PostMapping
    public Result<Dataset> createDataset(@RequestBody Dataset dataset) {
        return Result.success("知识库创建成功", datasetService.createDataset(dataset));
    }

    @Operation(summary = "分页查询知识库列表")
    @GetMapping
    public Result<PageResult<Dataset>> listDatasets(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        return Result.success(datasetService.listDatasets(keyword, pageNum, pageSize));
    }

    @Operation(summary = "删除知识库数据集 (级联清理文档与切片)")
    @SaCheckRole(value = {"OWNER", "ADMIN"}, mode = SaMode.OR)
    @DeleteMapping("/{id}")
    public Result<Void> deleteDataset(@PathVariable Long id) {
        datasetService.deleteDataset(id);
        return Result.success("知识库及切片已成功清理", null);
    }
}

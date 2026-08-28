package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.agentforge.service.rag.RagService;
import com.agentforge.vo.Citation;
import com.agentforge.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RAG 混合检索与调试接口
 */
@Tag(name = "05. RAG 混合检索与调试", description = "提供三路混合检索 (pgvector HNSW + BM25 + RRF + Reranker) 调试与溯源验证")
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
@SaCheckLogin
public class RagController {

    private final RagService ragService;

    @Operation(summary = "执行三路混合检索并返回溯源引用 (Citations)")
    @PostMapping("/search")
    public Result<List<Citation>> search(@RequestBody RagSearchRequest request) {
        int topK = request.getTopK() > 0 ? request.getTopK() : 5;
        double minScore = request.getMinScore() > 0 ? request.getMinScore() : 0.4;
        List<Citation> citations = ragService.retrieveCitations(request.getDatasetIds(), request.getQuery(), topK, minScore);
        return Result.success("检索完成", citations);
    }

    @Data
    @Schema(description = "RAG 检索请求参数")
    public static class RagSearchRequest {

        @NotEmpty(message = "知识库数据集 ID 列表不能为空")
        @Schema(description = "检索范围数据集 ID 列表", example = "[1]")
        private List<Long> datasetIds;

        @NotBlank(message = "查询 Query 不能为空")
        @Schema(description = "用户自然语言查询", example = "微服务架构中的多租户数据隔离方案是什么？")
        private String query;

        @Schema(description = "召回 Top-K 数量", example = "5")
        private int topK = 5;

        @Schema(description = "相似度最低阈值", example = "0.45")
        private double minScore = 0.45;
    }
}

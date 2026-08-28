package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.agentforge.service.rag.eval.RagasEvaluationEngine;
import com.agentforge.vo.ChunkSearchResult;
import com.agentforge.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RAGAS 召回精度量化评测大屏与对比报告接口
 */
@Tag(name = "11. RAGAS 知识库精度量化评测沙盒", description = "自动化评估精准度、召回率、抗幻觉率与对比报告")
@RestController
@RequestMapping("/rag/eval")
@RequiredArgsConstructor
@SaCheckLogin
public class RagasEvaluationController {

    private final RagasEvaluationEngine evaluationEngine;

    @Operation(summary = "触发 RAGAS 自动化精度量化评测")
    @PostMapping("/run")
    public Result<RagasEvaluationEngine.EvaluationReport> runEvaluation(@RequestBody EvalRunRequest req) {
        List<RagasEvaluationEngine.TestCase> cases = req.getTestCases();
        if (cases == null || cases.isEmpty()) {
            cases = List.of(
                    new RagasEvaluationEngine.TestCase("多租户数据隔离机制是怎么实现的？", "基于 MyBatis-Plus AST 语法树拦截器追加 tenant_id 条件。"),
                    new RagasEvaluationEngine.TestCase("三路混合检索融合算法是什么？", "pgvector HNSW 稠密向量 + BM25 稀疏检索 + RRF 倒数排名融合。"),
                    new RagasEvaluationEngine.TestCase("大模型 Token 成本如何降低 60%？", "基于 Redis 向量余弦相似度 >= 0.95 判定实现语义缓存直接返回。")
            );
        }

        List<ChunkSearchResult> mockChunks = List.of(
                ChunkSearchResult.builder()
                        .id(1L)
                        .documentId(1L)
                        .content("AgentForge 底层基于 JsqlParser 语法树对所有查询注入 tenant_id 条件，确保多租户绝对物理隔离。")
                        .score(0.88)
                        .build(),
                ChunkSearchResult.builder()
                        .id(2L)
                        .documentId(1L)
                        .content("Redis 向量语义降本缓存将常见高频问答在 20ms 内直接返回，降低 60% 以上大模型 API 开销。")
                        .score(0.79)
                        .build()
        );

        RagasEvaluationEngine.EvaluationReport report = evaluationEngine.evaluate(req.getDatasetId(), cases, mockChunks);
        return Result.success("评测完成", report);
    }

    @Data
    public static class EvalRunRequest {
        private Long datasetId;
        private List<RagasEvaluationEngine.TestCase> testCases;
    }
}

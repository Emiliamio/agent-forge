package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.agentforge.entity.DocumentChunk;
import com.agentforge.service.rag.chunk.ChunkManagementService;
import com.agentforge.vo.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库切片单条在线热编辑与状态管理接口
 */
@Tag(name = "14. 知识库切片单条热编辑与状态管理", description = "支持切片在线微调、实时重算向量与一键禁用")
@RestController
@RequestMapping("/rag/chunks")
@RequiredArgsConstructor
@SaCheckLogin
public class ChunkManagementController {

    private final ChunkManagementService chunkService;

    @Operation(summary = "分页获取指定文档的分块列表")
    @GetMapping("/document/{documentId}")
    public Result<Page<DocumentChunk>> listChunks(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<DocumentChunk> result = chunkService.listChunks(documentId, page, size);
        return Result.success(result);
    }

    @Operation(summary = "在线热编辑单条切片文本 (自动重算向量并驱逐旧缓存)")
    @PutMapping("/{chunkId}")
    public Result<DocumentChunk> updateChunk(
            @PathVariable Long chunkId,
            @RequestBody UpdateChunkRequest req
    ) {
        DocumentChunk chunk = chunkService.updateChunkContent(chunkId, req.getContent());
        return Result.success("切片更新成功，向量已自动重新计算", chunk);
    }

    @Operation(summary = "一键启用/禁用切片")
    @PutMapping("/{chunkId}/toggle")
    public Result<Void> toggleChunk(
            @PathVariable Long chunkId,
            @RequestParam boolean isPublic
    ) {
        chunkService.toggleChunkStatus(chunkId, isPublic);
        return Result.success(isPublic ? "切片已启用" : "切片已禁用 (检索时将自动排除)", null);
    }

    @Data
    public static class UpdateChunkRequest {
        private String content;
    }
}

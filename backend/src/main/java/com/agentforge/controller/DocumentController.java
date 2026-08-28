package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.agentforge.entity.Document;
import com.agentforge.service.rag.DocumentService;
import com.agentforge.vo.PageResult;
import com.agentforge.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档上传与解析管理接口
 */
@Tag(name = "04. 知识库文档管理", description = "提供 PDF/DOCX/MD/TXT 文档上传、异步切块与切片管理")
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@SaCheckLogin
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "上传文档并触发 RAG 管道解析切片 (支持 PDF/DOCX/MD/TXT)")
    @SaCheckRole(value = {"OWNER", "ADMIN", "EDITOR"}, mode = SaMode.OR)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Document> uploadDocument(
            @RequestParam("datasetId") Long datasetId,
            @RequestPart("file") MultipartFile file
    ) {
        Document document = documentService.uploadAndParse(datasetId, file);
        return Result.success("文档已接收，正在异步执行分块与向量化入库", document);
    }

    @Operation(summary = "分页查询指定知识库下的文档列表")
    @GetMapping("/list")
    public Result<PageResult<Document>> listDocuments(
            @RequestParam("datasetId") Long datasetId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        return Result.success(documentService.listDocuments(datasetId, pageNum, pageSize));
    }

    @Operation(summary = "删除指定文档及关联的切片向量")
    @SaCheckRole(value = {"OWNER", "ADMIN", "EDITOR"}, mode = SaMode.OR)
    @DeleteMapping("/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return Result.success("文档及切片已成功清理", null);
    }
}

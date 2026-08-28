package com.agentforge.service.rag;

import com.agentforge.entity.Document;
import com.agentforge.vo.PageResult;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档服务接口
 */
public interface DocumentService extends IService<Document> {

    /**
     * 上传并异步解析文档切片
     */
    Document uploadAndParse(Long datasetId, MultipartFile file);

    /**
     * 分页查询指定知识库下的文档列表
     */
    PageResult<Document> listDocuments(Long datasetId, long pageNum, long pageSize);

    /**
     * 删除单个文档及其所有切片
     */
    void deleteDocument(Long documentId);

    /**
     * 同步处理文档分块与向量化入库 (核心 RAG 管道入口)
     */
    void processDocument(Long documentId, byte[] fileBytes, String fileName, String fileType);
}

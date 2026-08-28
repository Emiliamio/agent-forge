package com.agentforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档分块与高维向量实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_chunk")
public class DocumentChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long datasetId;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    /**
     * 1536/1024 维稠密向量 (入库格式: "[0.123, -0.456, ...]")
     */
    private String embedding;

    private Integer tokenCount;

    /**
     * 结构化元数据 (JSONB 格式：包含 page, title, source 等)
     */
    private String metadata;

    /**
     * 全文分词向量 (由 PG Trigger 自动维护)
     */
    @TableField(exist = false)
    private String tsvContent;

    private LocalDateTime createdAt;
}

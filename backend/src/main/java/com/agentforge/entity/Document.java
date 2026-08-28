package com.agentforge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 知识库文档明细实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("document")
public class Document extends BaseEntity {

    private Long tenantId;

    private Long datasetId;

    private String name;

    private String filePath;

    private Long fileSize;

    private String fileType; // PDF, DOCX, MD, TXT

    private Integer charCount;

    private Integer chunkCount;

    private String parseStatus; // PENDING, PARSING, SUCCESS, FAILED

    private String errorMsg;
}

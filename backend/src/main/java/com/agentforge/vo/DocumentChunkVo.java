package com.agentforge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识库分块视图对象")
public class DocumentChunkVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long datasetId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String embedding;
    private Integer tokenCount;
    private String metadata;
    private String tsvContent;
    private LocalDateTime createdAt;
}

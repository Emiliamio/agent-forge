package com.agentforge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库切片检索结果 VO (包含相似度得分与溯源元数据)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "切片检索召回结果")
public class ChunkSearchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "切片 ID")
    private Long id;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "知识库数据集 ID")
    private Long datasetId;

    @Schema(description = "原始文档 ID")
    private Long documentId;

    @Schema(description = "切片序号")
    private Integer chunkIndex;

    @Schema(description = "切片文本内容")
    private String content;

    @Schema(description = "结构化元数据 (标题、页码等)")
    private String metadata;

    @Schema(description = "Token 数量")
    private Integer tokenCount;

    @Schema(description = "综合相似度/重排得分", example = "0.895")
    private Double score;

    @Schema(description = "召回渠道类型 (VECTOR, KEYWORD, HYBRID)")
    private String recallType;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}

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
@Schema(description = "知识库数据集视图对象")
public class DatasetVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private String avatar;
    private String embeddingModel;
    private Integer embeddingDim;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

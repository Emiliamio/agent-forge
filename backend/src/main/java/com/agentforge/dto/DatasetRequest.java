package com.agentforge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识库数据集创建请求")
public class DatasetRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "知识库名称不能为空")
    @Schema(description = "知识库名称")
    private String name;

    @Schema(description = "知识库描述")
    private String description;

    @Schema(description = "图标")
    private String avatar;

    @Schema(description = "向量化模型")
    private String embeddingModel;

    @Schema(description = "向量维度")
    private Integer embeddingDim;

    @Schema(description = "分块大小")
    private Integer chunkSize;

    @Schema(description = "分块重叠度")
    private Integer chunkOverlap;

    @Schema(description = "状态")
    private Integer status;
}

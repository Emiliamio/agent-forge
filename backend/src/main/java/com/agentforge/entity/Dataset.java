package com.agentforge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 知识库数据集实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("dataset")
public class Dataset extends BaseEntity {

    private Long tenantId;

    private String name;

    private String description;

    private String avatar;

    private String embeddingModel;

    private Integer embeddingDim;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private Integer status; // 1: 正常, 0: 下线
}

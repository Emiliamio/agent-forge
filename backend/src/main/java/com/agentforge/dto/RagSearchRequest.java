package com.agentforge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RAG 混合检索请求")
public class RagSearchRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String query;
    private List<Long> datasetIds;
    private Integer topK;
}

package com.agentforge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RAGAS 评测运行请求")
public class EvalRunRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String evalType;
    private List<Map<String, Object>> testCases;
}

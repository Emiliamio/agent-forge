package com.agentforge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建会话请求")
public class CreateSessionRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long appId;
    private String title;
}

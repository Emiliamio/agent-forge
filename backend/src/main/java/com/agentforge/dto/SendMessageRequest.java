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
@Schema(description = "发送会话消息请求")
public class SendMessageRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "消息内容不能为空")
    private String message;
}

package com.agentforge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 创建/更新智能体应用请求传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "智能体应用创建与修改请求对象")
public class AgentAppRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "智能体名称不能为空")
    @Schema(description = "智能体名称", example = "企业差旅合规助手")
    private String name;

    @Schema(description = "智能体功能描述", example = "专用于解答员工关于住宿、交通与差旅标准报销规范")
    private String description;

    @Schema(description = "智能体头像图标", example = "robot-travel")
    private String avatar;

    @Schema(description = "应用形态分类 (CHATBOT / AGENT / WORKFLOW)", example = "AGENT")
    private String appType;

    @Schema(description = "系统角色 System Prompt 预设词")
    private String systemPrompt;

    @Schema(description = "模型配置参数 JSON 字符串")
    private String modelConfig;

    @Schema(description = "Function Calling 挂载工具列表 JSON 字符串")
    private String toolsConfig;

    @Schema(description = "关联知识库 ID 数组 JSON 字符串")
    private String datasetIds;

    @Schema(description = "启用状态 (1: 启用, 0: 禁用)", example = "1")
    private Integer status;

    @Schema(description = "是否允许公开免登录挂件嵌入访问 (1: 开启, 0: 关闭)", example = "1")
    private Integer publicEnabled;

    @Schema(description = "当前生效的挂件签名密钥或明文安全 Token")
    private String publicToken;

    @Schema(description = "平滑过渡期的旧挂件密钥")
    private String publicTokenPrev;
}

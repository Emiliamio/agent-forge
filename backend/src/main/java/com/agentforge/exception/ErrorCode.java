package com.agentforge.exception;

import lombok.Getter;

/**
 * 业务全局错误码枚举
 */
@Getter
public enum ErrorCode {

    // 基础系统错误 (200, 400 - 500)
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "请求参数错误"),
    UNAUTHORIZED(401, "账号未登录或登录已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "请求资源未找到"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    SYSTEM_ERROR(500, "系统开小差了，请稍后重试"),

    // 多租户与权限相关 (1001 - 1099)
    TENANT_NOT_FOUND(1001, "租户不存在或已被禁用"),
    TENANT_HEADER_MISSING(1002, "请求缺少租户标识信息"),
    TENANT_WALLET_EXHAUSTED(1003, "租户账户余额不足，请充值后继续使用"),
    USER_NOT_FOUND(1004, "用户不存在"),
    USER_PASSWORD_ERROR(1005, "用户名或密码错误"),
    USER_DISABLED(1006, "用户已被冻结禁用"),
    API_KEY_INVALID(1007, "API Key 无效或已过期"),
    RATE_LIMIT_EXCEEDED(1008, "请求触发限流，请稍后重试"),

    // 知识库与 RAG 相关 (2001 - 2099)
    DATASET_NOT_FOUND(2001, "知识库数据集不存在"),
    DOCUMENT_NOT_FOUND(2002, "文档不存在"),
    DOCUMENT_PARSE_FAILED(2003, "文档解析处理失败"),
    FILE_TYPE_NOT_SUPPORTED(2004, "不支持的文件解析格式"),
    FILE_EMPTY(2005, "上传文件内容为空"),
    EMBEDDING_FAILED(2006, "文档向量化计算失败"),

    // Agent 与工作流相关 (3001 - 3099)
    AGENT_APP_NOT_FOUND(3001, "智能体应用不存在"),
    WORKFLOW_NOT_FOUND(3002, "工作流定义不存在"),
    WORKFLOW_CYCLE_DETECTED(3003, "工作流存在环路依赖，拓扑校验失败"),
    WORKFLOW_NODE_EXECUTE_FAILED(3004, "工作流节点执行失败"),
    WORKFLOW_TIMEOUT(3005, "工作流执行超时"),
    TOOL_EXECUTE_FAILED(3006, "工具调度执行失败"),

    // 大模型服务相关 (4001 - 4099)
    LLM_API_ERROR(4001, "大模型 API 调用异常"),
    LLM_API_TIMEOUT(4002, "大模型响应超时"),
    LLM_QUOTA_EXCEEDED(4003, "大模型提供商配额超限"),
    LLM_FALLBACK_ALL_FAILED(4004, "所有备用大模型均调用失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

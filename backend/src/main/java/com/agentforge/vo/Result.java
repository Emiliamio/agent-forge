package com.agentforge.vo;

import com.agentforge.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一 API 响应结果包装类
 *
 * @param <T> 数据负载泛型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "全局统一响应包装")
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "业务响应状态码", example = "200")
    private int code;

    @Schema(description = "响应消息提示", example = "操作成功")
    private String message;

    @Schema(description = "业务数据实体")
    private T data;

    @Schema(description = "响应时间戳", example = "1724832000000")
    private long timestamp;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Result<T> success(String message, T data) {
        return Result.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return Result.<T>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Result<T> fail(int code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Result<T> fail(String message) {
        return Result.<T>builder()
                .code(ErrorCode.SYSTEM_ERROR.getCode())
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}

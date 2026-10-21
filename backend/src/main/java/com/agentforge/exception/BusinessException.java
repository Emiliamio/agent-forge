package com.agentforge.exception;

import lombok.Getter;

/**
 * 自定义业务运行时异常
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String message;
    private final Integer retryAfterSeconds;
    private final Long rateLimitLimit;
    private final Long rateLimitRemaining;
    private final Long rateLimitReset;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.retryAfterSeconds = null;
        this.rateLimitLimit = null;
        this.rateLimitRemaining = null;
        this.rateLimitReset = null;
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.code = errorCode.getCode();
        this.message = customMessage;
        this.retryAfterSeconds = null;
        this.rateLimitLimit = null;
        this.rateLimitRemaining = null;
        this.rateLimitReset = null;
    }

    public BusinessException(ErrorCode errorCode, String customMessage, Integer retryAfterSeconds) {
        super(customMessage);
        this.code = errorCode.getCode();
        this.message = customMessage;
        this.retryAfterSeconds = retryAfterSeconds;
        this.rateLimitLimit = null;
        this.rateLimitRemaining = null;
        this.rateLimitReset = null;
    }

    public BusinessException(ErrorCode errorCode, String customMessage, Integer retryAfterSeconds, Long limit, Long remaining, Long reset) {
        super(customMessage);
        this.code = errorCode.getCode();
        this.message = customMessage;
        this.retryAfterSeconds = retryAfterSeconds;
        this.rateLimitLimit = limit;
        this.rateLimitRemaining = remaining;
        this.rateLimitReset = reset;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
        this.retryAfterSeconds = null;
        this.rateLimitLimit = null;
        this.rateLimitRemaining = null;
        this.rateLimitReset = null;
    }

    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.SYSTEM_ERROR.getCode();
        this.message = message;
        this.retryAfterSeconds = null;
        this.rateLimitLimit = null;
        this.rateLimitRemaining = null;
        this.rateLimitReset = null;
    }
}

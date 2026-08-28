package com.agentforge.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.agentforge.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局统一异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务自定义异常拦截
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [URI: {}]: code={}, message={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常 (JSR-303)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        String message = fieldErrors.stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验异常 [URI: {}]: {}", request.getRequestURI(), message);
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        String message = e.getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数绑定异常 [URI: {}]: {}", request.getRequestURI(), message);
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * Sa-Token 未登录拦截
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLoginException(NotLoginException e, HttpServletRequest request) {
        log.warn("未登录访问 [URI: {}]: type={}, message={}", request.getRequestURI(), e.getType(), e.getMessage());
        return Result.fail(ErrorCode.UNAUTHORIZED.getCode(), "认证失败，请重新登录");
    }

    /**
     * Sa-Token 无权限拦截
     */
    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotPermissionException(Exception e, HttpServletRequest request) {
        log.warn("权限不足 [URI: {}]: {}", request.getRequestURI(), e.getMessage());
        return Result.fail(ErrorCode.FORBIDDEN.getCode(), "操作权限不足");
    }

    /**
     * 404 资源未找到
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        return Result.fail(ErrorCode.NOT_FOUND.getCode(), "请求资源不存在: " + request.getRequestURI());
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return Result.fail(ErrorCode.METHOD_NOT_ALLOWED.getCode(), "请求方法不支持: " + e.getMethod());
    }

    /**
     * 系统未知未知异常拦截
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统未知异常 [URI: {}]", request.getRequestURI(), e);
        return Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), "系统处理异常，请联系管理员");
    }
}

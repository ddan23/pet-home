package com.hnist.common.exception;

import com.hnist.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常出口：各类异常统一翻译成 Result，异常细节与堆栈只进日志，不回传前端。
 * 暴露 SQL、类名或堆栈等于把内部结构交给调用方，因此前端只拿可读文案与稳定的业务码。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务码已是 HTTP 语义，原样透传；HTTP 状态交给 ResultStatusAdvice，避免两处各维护一份映射。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        return Result.fail(exception.getCode(), exception.getMessage());
    }

    /** AI 外部依赖失败单独记 warn 并回 503，让前端按“稍后再试”处理，而不是笼统的 500。 */
    @ExceptionHandler(AiUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleAiUnavailableException(AiUnavailableException exception) {
        log.warn("AI服务不可用：{}", exception.getMessage());
        return Result.fail(503, exception.getMessage());
    }

    /** 请求体字段校验失败只回第一条错误：字段清单属于内部结构，逐条回传没有额外价值。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidationException(Exception exception) {
        BindingResult bindingResult = exception instanceof MethodArgumentNotValidException validException
                ? validException.getBindingResult()
                : ((BindException) exception).getBindingResult();
        return Result.fail(400, firstValidationMessage(bindingResult));
    }

    /** 方法参数上的约束校验（路径/查询参数）沿用框架生成的中文提示，直接回 400。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        return Result.fail(400, exception.getMessage());
    }

    /** 请求体无法反序列化时换成固定文案，原始 Jackson 报错含字段名与目标类，不能回传。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMessageNotReadableException() {
        return Result.fail(400, "请求参数格式不正确");
    }

    /** 未知静态路径回 404，与 Spring 默认行为一致，不额外暴露路由表。 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFoundException() {
        return Result.fail(404, "资源不存在");
    }

    /** 只回缺失的参数名，便于联调，同时不泄露方法签名以外的信息。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParameterException(MissingServletRequestParameterException exception) {
        return Result.fail(400, "缺少请求参数：" + exception.getParameterName());
    }

    /** 路径存在但方法不匹配，回 405 而不是 404，方便前端定位调用方式问题。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupportedException() {
        return Result.fail(405, "请求方法不支持");
    }

    /** 上传超限回 413，文案中的 5MB 与 spring.servlet.multipart.max-file-size 保持一致。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result<Void> handleMaxUploadSizeExceededException() {
        return Result.fail(413, "上传文件不能超过5MB");
    }

    /** 兜底分支：完整堆栈只写 error 日志，前端只看到通用提示，避免暴露实现细节。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknownException(Exception exception) {
        log.error("未处理的系统异常", exception);
        return Result.fail(500, "服务器内部错误");
    }

    /** 只取第一条校验错误；没有错误对象时给出通用文案，避免返回 null 让前端显示空白提示。 */
    private String firstValidationMessage(BindingResult bindingResult) {
        return bindingResult.getAllErrors().isEmpty()
                ? "请求参数校验失败"
                : bindingResult.getAllErrors().get(0).getDefaultMessage();
    }
}

package com.hnist.common.exception;

import lombok.Getter;

/**
 * 业务异常：code 直接复用 HTTP 状态码，消息会原样返回前端，因此只能写用户可读文案，不能带内部标识或堆栈。
 * 响应体由 GlobalExceptionHandler 包装，HTTP 状态再由 ResultStatusAdvice 按同一 code 对齐，两处不会漂移。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务码，实际取值 400/401/403/404/409/413/429/500，与 HTTP 状态一一对应。 */
    private final int code;

    /** 缺省按 400 处理：参数与业务规则校验失败占绝大多数。 */
    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}

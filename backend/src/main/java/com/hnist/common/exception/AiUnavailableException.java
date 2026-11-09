package com.hnist.common.exception;

/** AI 依赖不可用：模型未配置、超时或上游 5xx 时抛出，全局处理器统一转 503，调用方据此降级或转人工。 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

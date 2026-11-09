package com.hnist.common.exception;

import com.hnist.common.result.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 把统一响应里的业务码同步成 HTTP 状态码：旧页面只看响应体 code，新客户端依赖标准状态码，两者必须一致。 */
@ControllerAdvice
public class ResultStatusAdvice implements ResponseBodyAdvice<Object> {
    // 全项目「业务码 401/404/400 会变成对应 HTTP 状态码」的口径出处都在本类；控制器注释提到状态码时以此为准。

    /** 对所有返回值生效；真正需要改写的只有 Result，其它类型在 beforeBodyWrite 里原样返回。 */
    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /** 只对 code >= 400 的响应改状态；非标准 HTTP 码无法解析时回落到 400，避免写入非法状态行。 */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?> result && result.getCode() != null && result.getCode() >= 400) {
            HttpStatus status = HttpStatus.resolve(result.getCode());
            response.setStatusCode(status == null ? HttpStatus.BAD_REQUEST : status);
        }
        return body;
    }
}

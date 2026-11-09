package com.hnist.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 统一接口响应：code 为业务码，200 表示成功；code >= 400 时由 ResultStatusAdvice 同步写成 HTTP 状态码，
 * 无法映射到标准状态码的一律回 400，因此前端既可只看 code，也可只看 HTTP status。
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    // 沿用 HTTP 语义：400 参数错误、401 未登录、403 无权限、404 资源不存在，其余见 GlobalExceptionHandler。
    private Integer code;

    // 成功时默认 success，也可带业务提示文案；失败时必须给出可直接展示的原因。
    private String msg;

    // 为 null 时不输出该字段（类上的 NON_NULL），前端按是否存在 data 区分空结果与空值。
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> ok(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(400, msg, null);
    }
}

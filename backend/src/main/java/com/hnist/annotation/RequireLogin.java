package com.hnist.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在 Controller 方法或类上，由 TokenInterceptor 读取（方法级优先于类级）。
 * 未解析出登录用户（无 token、校验失败或账号状态非 normal）时直接写回 401「请先登录」并拦截请求。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
}

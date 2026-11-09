package com.hnist.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在 Controller 方法或类上，由 TokenInterceptor 读取（方法级优先于类级），value 为允许的角色（user、business、admin）。
 * 登录用户的 role 不在列表内时返回 403「无权限」；未登录时返回 401「请先登录」。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    String[] value();
}

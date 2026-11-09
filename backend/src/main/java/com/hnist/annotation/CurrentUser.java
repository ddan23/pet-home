package com.hnist.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 方法参数上，由 CurrentUserArgumentResolver 读取并注入 TokenInterceptor 解析出的登录用户。
 * required 默认 true：未登录时抛 401「请先登录」；置为 false 时未登录注入 null，由业务自行判断。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    boolean required() default true;
}

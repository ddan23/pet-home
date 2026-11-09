package com.hnist.security;

import com.hnist.annotation.CurrentUser;

import com.hnist.common.exception.BusinessException;
import com.hnist.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 将 TokenInterceptor 预先写入请求属性的登录用户注入 @CurrentUser 参数，自身不解析令牌。 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ATTRIBUTE = "currentUser";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && User.class.isAssignableFrom(parameter.getParameterType());
    }

    /** required=true 且无登录态时抛业务 401；required=false 时返回 null，由方法自行处理匿名场景。 */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        User user = request == null ? null : (User) request.getAttribute(USER_ATTRIBUTE);
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        if (user == null && annotation != null && annotation.required()) {
            throw new BusinessException(401, "请先登录");
        }
        return user;
    }
}

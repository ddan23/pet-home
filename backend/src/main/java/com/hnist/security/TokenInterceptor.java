package com.hnist.security;

import com.hnist.annotation.RequireLogin;
import com.hnist.annotation.RequireRole;
import com.hnist.common.constant.AccountStatus;
import com.hnist.common.result.Result;
import com.hnist.config.JwtProperties;
import com.hnist.entity.User;
import com.hnist.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * JWT 登录与角色校验拦截器：解析出的用户写入请求属性，供 @CurrentUser 参数注入。
 * 校验失败以统一 Result JSON 写回 401/403，HTTP 状态码与业务码一致。
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;

    public TokenInterceptor(UserService userService, JwtTokenProvider tokenProvider,
                            ObjectMapper objectMapper, JwtProperties jwtProperties,
                            TokenBlacklistService tokenBlacklistService) {
        this.userService = userService;
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
        this.jwtProperties = jwtProperties;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * 非 HandlerMethod（静态资源、错误页等）直接放行；
     * 注解查找方法级优先于类级：方法上的声明覆盖类上的 @RequireRole/@RequireLogin。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 先解析用户并写入请求属性：即使请求最终被放行，@CurrentUser 也能拿到登录态（可为 null）。
        User user = resolveUser(request);
        if (user != null) {
            request.setAttribute(com.hnist.security.CurrentUserArgumentResolver.USER_ATTRIBUTE, user);
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireLogin requireLogin = findAnnotation(handlerMethod, RequireLogin.class);
        RequireRole requireRole = findAnnotation(handlerMethod, RequireRole.class);
        if (requireLogin != null && user == null) {
            writeError(response, 401, "请先登录");
            return false;
        }
        if (requireRole != null && !hasRole(user, requireRole.value())) {
            writeError(response, user == null ? 401 : 403, user == null ? "请先登录" : "无权限");
            return false;
        }
        return true;
    }

    /** 令牌无效、用户不存在或账号被禁用均视为未登录，返回 null 而不中断请求。 */
    private User resolveUser(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            return null;
        }
        try {
            if (!tokenProvider.validateToken(token)) {
                return null;
            }
            // 已登出的令牌签名仍然有效，必须查黑名单拦截；Redis 故障时黑名单按未吊销放行（fail-open）
            if (tokenBlacklistService.isRevoked(token)) {
                return null;
            }
            // 账号必须在库且状态为 NORMAL，禁用账号等同未登录。
            User user = userService.findById(tokenProvider.getUserId(token));
            return user != null
                    && AccountStatus.NORMAL.getCode().equals(user.getStatus()) ? user : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String resolveToken(HttpServletRequest request) {
        // 优先取 Authorization: Bearer 头，其次取配置命名的 Cookie。
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (jwtProperties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private <T extends java.lang.annotation.Annotation> T findAnnotation(HandlerMethod handlerMethod,
                                                                        Class<T> annotationType) {
        T annotation = handlerMethod.getMethodAnnotation(annotationType);
        return annotation != null ? annotation : handlerMethod.getBeanType().getAnnotation(annotationType);
    }

    private boolean hasRole(User user, String[] roles) {
        if (user == null) {
            return false;
        }
        for (String role : roles) {
            if (role.equals(user.getRole())) {
                return true;
            }
        }
        return false;
    }

    /** 以统一 Result JSON 写回而非只给状态码，前端按同一格式解析错误。 */
    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
    }
}

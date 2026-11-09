package com.hnist.controller;

import com.hnist.annotation.CurrentUser;
import com.hnist.common.result.Result;
import com.hnist.dto.LoginRequest;
import com.hnist.dto.ChangePasswordRequest;
import com.hnist.dto.RegisterRequest;
import com.hnist.vo.TokenInfo;
import com.hnist.config.JwtProperties;
import com.hnist.entity.User;
import com.hnist.service.UserService;
import com.hnist.security.JwtTokenProvider;
import com.hnist.security.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** 登录与令牌校验控制器。 */
@RestController
public class LoginController {

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;

    public LoginController(UserService userService, JwtTokenProvider tokenProvider,
                           JwtProperties jwtProperties, TokenBlacklistService tokenBlacklistService) {
        this.userService = userService;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * 登录成功后 JWT 写入 HttpOnly Cookie，响应体中 token 恒为 null；
     * 凭证或角色不符返回业务码 401，由 ResultStatusAdvice 同步为 HTTP 401。
     */
    @PostMapping({"/api/login", "/api/v1/auth/login"})
    public Result<TokenInfo> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse) {
        User user = userService.validate(request.getUsername(), request.getPassword(), request.getRole());
        if (user == null) {
            return Result.fail(401, "用户名、密码或角色错误");
        }
        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        setAuthCookie(servletRequest, servletResponse, token, jwtProperties.getExpirationSeconds());
        return Result.ok(new TokenInfo(user.getId(), null, user.getRole(),
                user.getNickname(), user.getAvatar()));
    }

    /** 未登录以业务码 401 表达，经 ResultStatusAdvice 同步为 HTTP 401。 */
    @GetMapping({"/api/token/check", "/api/v1/auth/me"})
    public Result<TokenInfo> checkToken(@CurrentUser(required = false) User user) {
        if (user == null) {
            return Result.fail(401, "未登录或token已过期");
        }
        return Result.ok(new TokenInfo(user.getId(), null, user.getRole(),
                user.getNickname(), user.getAvatar()));
    }

    @PostMapping("/api/v1/auth/register")
    public Result<TokenInfo> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletRequest servletRequest,
                                      HttpServletResponse servletResponse) {
        User user = userService.register(request);
        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        setAuthCookie(servletRequest, servletResponse, token, jwtProperties.getExpirationSeconds());
        return Result.ok("注册成功", new TokenInfo(user.getId(), null, user.getRole(),
                user.getNickname(), user.getAvatar()));
    }

    /** 清除浏览器 Cookie 并把令牌写入 Redis 黑名单：被复制的令牌在剩余有效期内同样会被拦截。 */
    @PostMapping("/api/v1/auth/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        tokenBlacklistService.revoke(resolveToken(request));
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getCookieName(), "")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite("Lax")
                .path(cookiePath(request))
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return Result.ok();
    }

    /** 与 TokenInterceptor 同源解析：请求头 Bearer 优先，其次登录 Cookie，保证吊销的正是调用方持有的那枚令牌。 */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (jwtProperties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** 未登录返回业务码 401（经 ResultStatusAdvice 同步为 HTTP 401），因此用户注入声明为 required=false。 */
    @PostMapping("/api/v1/auth/password")
    public Result<Void> changePassword(@CurrentUser(required = false) User user,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        userService.changePassword(user.getId(), request.getCurrentPassword(), request.getNewPassword());
        return Result.ok("密码修改成功", null);
    }

    private void setAuthCookie(HttpServletRequest request, HttpServletResponse response,
                               String token, long expirationSeconds) {
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getCookieName(), token)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite("Lax")
                .path(cookiePath(request))
                .maxAge(Duration.ofSeconds(expirationSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String cookiePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return contextPath == null || contextPath.isBlank() ? "/" : contextPath;
    }
}

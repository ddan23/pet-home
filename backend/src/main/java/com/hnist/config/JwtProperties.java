package com.hnist.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 绑定 app.jwt.*；secret 来自环境变量且无默认值，@Validated 让缺配/弱密钥在启动期就失败，而不是上线后被伪造 token。 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥（JWT_SECRET）：必须 ≥32 字符；泄露即可伪造任意用户登录态，故强制校验不给默认值。 */
    @NotBlank(message = "JWT_SECRET不能为空")
    @Size(min = 32, message = "JWT_SECRET长度不能少于32个字符")
    private String secret;

    /** token 有效期，单位秒（JWT_EXPIRATION_SECONDS，默认 86400 即 1 天）；同一值也用作登录 cookie 的 maxAge。 */
    private long expirationSeconds = 86_400L;

    /** 登录态 cookie 名（JWT_COOKIE_NAME，默认 pet_home_token），登出时按同名清除。 */
    private String cookieName = "pet_home_token";

    /** 是否给 cookie 加 Secure 标记（JWT_COOKIE_SECURE，默认 false 适配本地 HTTP；生产应置 true 只走 HTTPS）。 */
    private boolean cookieSecure = false;
}

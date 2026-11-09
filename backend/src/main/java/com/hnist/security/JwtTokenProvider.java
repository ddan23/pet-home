package com.hnist.security;

import com.hnist.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 令牌生成与解析。
 * 使用 HMAC-SHA 对称签名（密钥来自配置），subject 存 userId，username 与 role 放自定义 claim。
 */
@Component
public class JwtTokenProvider {

    private final JwtProperties properties;

    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 无状态签发：过期时间取配置 expirationSeconds，服务端不保存会话。 */
    public String generateToken(Integer userId, String username, String role) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(properties.getExpirationSeconds());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    public Integer getUserId(String token) {
        return Integer.valueOf(parseClaims(token).getSubject());
    }

    /** 返回令牌过期时刻的毫秒时间戳；签名或格式非法时抛出运行时异常，调用方须先 validateToken。 */
    public long getExpirationMillis(String token) {
        return parseClaims(token).getExpiration().getTime();
    }

    /** 解析、签名校验或过期任一失败都返回 false，不抛异常，调用方据此判断登录态。 */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

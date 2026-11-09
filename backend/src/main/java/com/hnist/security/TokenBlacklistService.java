package com.hnist.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JWT 吊销黑名单：登出时把令牌哈希写入 Redis，剩余有效期一过 key 自动消失，无需清理任务。
 * key 存哈希而不是原始令牌：Redis 里不落可登录的凭据明文，缩短泄露半径。
 * 黑名单采用 fail-open：Redis 故障期间放行（登出的令牌在故障窗口内仍可用，风险可接受），
 * 不采用 fail-close——那会让全部登录用户在 Redis 故障期间被登出，可用性代价更高。
 */
@Slf4j
@Service
public class TokenBlacklistService {

    /** 与缓存 key 前缀隔离：黑名单是安全状态，不是可丢失的加速数据。 */
    static final String KEY_PREFIX = "blacklist:token:";

    private static final String REVOKED_VALUE = "1";
    /** 故障告警限频：Redis 故障期间每个请求都会尝试查黑名单，30 秒最多一条 WARN。 */
    private static final long LOG_INTERVAL_MILLIS = 30_000L;

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider tokenProvider;
    private final AtomicLong lastLogAt = new AtomicLong();

    public TokenBlacklistService(StringRedisTemplate redisTemplate, JwtTokenProvider tokenProvider) {
        this.redisTemplate = redisTemplate;
        this.tokenProvider = tokenProvider;
    }

    /**
     * 吊销令牌：TTL 取令牌剩余有效期——过期令牌本来就无法通过签名校验，多留一秒都是无谓占用。
     * 令牌已过期或非法时跳过写入；Redis 故障不阻断登出响应（浏览器 Cookie 已由调用方清除）。
     */
    public void revoke(String token) {
        if (token == null || !tokenProvider.validateToken(token)) {
            return;
        }
        try {
            long remainingMillis = tokenProvider.getExpirationMillis(token) - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                return;
            }
            redisTemplate.opsForValue().set(revokedKey(token), REVOKED_VALUE,
                    Duration.ofMillis(remainingMillis));
        } catch (RuntimeException exception) {
            warnThrottled("写入吊销记录失败", exception);
        }
    }

    /** 命中返回 true；Redis 故障按 fail-open 放行，与类注释的安全权衡一致。 */
    public boolean isRevoked(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(revokedKey(token)));
        } catch (RuntimeException exception) {
            warnThrottled("查询吊销状态失败，按未吊销放行", exception);
            return false;
        }
    }

    /** 用 SHA-256 摘要做 key：同一令牌每次计算结果一致，且无法从哈希还原令牌。 */
    private String revokedKey(String token) {
        return KEY_PREFIX + sha256Hex(token);
    }

    private String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // JDK 必带 SHA-256，此分支只为满足受检异常；走到这里说明 JVM 环境损坏，直接失败暴露问题
            throw new IllegalStateException("SHA-256 摘要算法不可用", exception);
        }
    }

    private void warnThrottled(String action, RuntimeException cause) {
        long now = System.currentTimeMillis();
        long last = lastLogAt.get();
        if (now - last >= LOG_INTERVAL_MILLIS && lastLogAt.compareAndSet(last, now)) {
            log.warn("{}（黑名单降级为 fail-open）：{}", action, cause.getMessage());
        }
    }
}

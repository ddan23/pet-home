package com.hnist.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis ZSET 的真实滑动窗口限流：窗口内第 limit+1 个请求被拒，
 * 清理过期成员、容量判断、写入新成员、续期在同一个 Lua 脚本里原子完成，并发下不会超放。
 * key 按业务划分（如 ratelimit:ai:minute:{userId}），多实例共享同一份计数。
 * Redis 不可用时调用方必须自行降级，本类只如实抛出连接异常。
 */
@Component
public class RedisRateLimiter {

    /**
     * ARGV：1=当前毫秒，2=窗口毫秒，3=上限，4=成员唯一串。
     * ZADD 成员必须唯一：同毫秒并发用 UUID 兜底，避免相互覆盖导致少计数。
     */
    private static final String SLIDING_WINDOW_LUA = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1] - ARGV[2])
            if redis.call('ZCARD', KEYS[1]) < tonumber(ARGV[3]) then
                redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
                return 1
            end
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> slidingWindowScript =
            new DefaultRedisScript<>(SLIDING_WINDOW_LUA, Long.class);

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 尝试占一个窗口名额：true=放行并已记录，false=窗口已满；Redis 异常原样抛出由调用方决定降级。 */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long nowMillis = System.currentTimeMillis();
        Long allowed = redisTemplate.execute(slidingWindowScript, List.of(key),
                Long.toString(nowMillis),
                Long.toString(window.toMillis()),
                Integer.toString(limit),
                nowMillis + "-" + UUID.randomUUID());
        return allowed != null && allowed == 1L;
    }
}

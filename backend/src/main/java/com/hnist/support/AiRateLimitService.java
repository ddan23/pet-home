package com.hnist.support;

import com.hnist.common.exception.BusinessException;
import com.hnist.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 调用限流：每用户「每日总次数 + 每分钟滑动窗口」双重配额。
 * 计数存 Redis，多实例共享、重启不清零；Redis 不可用时显式降级为进程内存计数并限频告警，
 * 拒绝语义（429）两条路径完全一致，调用方无感知。
 */
@Slf4j
@Service
public class AiRateLimitService {

    // Redis key 前缀为包级可见：集成测试需要读取真实 key 断言计数确实落在 Redis
    static final String DAILY_KEY_PREFIX = "ratelimit:ai:daily:";
    static final String MINUTE_KEY_PREFIX = "ratelimit:ai:minute:";

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Duration MINUTE_WINDOW = Duration.ofMinutes(1);
    /** 降级告警的日志节流间隔：Redis 故障期间高频调用会让 WARN 刷屏，30 秒最多一条。 */
    private static final long FALLBACK_LOG_INTERVAL_MILLIS = 30_000L;

    private final AiProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RedisRateLimiter redisRateLimiter;

    // 内存降级路径：只在 Redis 故障期间生效，结构与 Redis 版一致（日计数 + 分钟滑窗）。
    private final Map<Integer, UserQuota> quotas = new ConcurrentHashMap<>();
    private final KeyedSlidingWindowLimiter minuteLimiter = new KeyedSlidingWindowLimiter();
    private final AtomicLong lastFallbackLogAt = new AtomicLong();

    public AiRateLimitService(AiProperties properties, StringRedisTemplate redisTemplate,
                              RedisRateLimiter redisRateLimiter) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.redisRateLimiter = redisRateLimiter;
    }

    /**
     * 校验并占用手额度，超限抛 {@link BusinessException}(429)。
     * 分钟窗先占位、日计数后累加：日超限时把 INCR 结果 DECR 回退，
     * 保证被拒绝的调用不消耗配额，也不会因回退产生计数漂移。
     */
    public void check(Integer userId) {
        LocalDate today = LocalDate.now(ZONE_ID);
        try {
            checkWithRedis(userId, today);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 连接异常、超时等 Redis 故障统一走内存路径；429 是业务结果不能被当成故障吞掉
            fallbackToLocal(userId, today, exception);
        }
    }

    private void checkWithRedis(Integer userId, LocalDate today) {
        String minuteKey = MINUTE_KEY_PREFIX + userId;
        if (!redisRateLimiter.tryAcquire(minuteKey, properties.getMinuteLimit(), MINUTE_WINDOW)) {
            throw new BusinessException(429, "操作过于频繁，请稍后再试");
        }

        // 日 key 带日期后缀：跨天自然切换新 key，旧 key 由 TTL 过期回收，无需清理任务
        String dailyKey = DAILY_KEY_PREFIX + userId + ":" + today.format(DAY_KEY);
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1L) {
            // 首次计数才设置过期：过期点取次日零点再加 1 小时时钟缓冲，防止边界时刻提前回收
            Duration untilExpire = Duration.between(LocalDateTime.now(ZONE_ID),
                    today.plusDays(1).atStartOfDay()).plus(Duration.ofHours(1));
            redisTemplate.expire(dailyKey, untilExpire);
        }
        if (dailyCount != null && dailyCount > properties.getDailyLimit()) {
            // 超限的这次不计入配额：立即回退 INCR，并发下每个请求只回退自己那次，净变化为零
            redisTemplate.opsForValue().decrement(dailyKey);
            throw new BusinessException(429, "今日 AI 使用次数已达上限");
        }
    }

    private void fallbackToLocal(Integer userId, LocalDate today, RuntimeException cause) {
        long now = System.currentTimeMillis();
        long last = lastFallbackLogAt.get();
        if (now - last >= FALLBACK_LOG_INTERVAL_MILLIS && lastFallbackLogAt.compareAndSet(last, now)) {
            log.warn("Redis 限流不可用，AI 配额降级为本机内存计数（重启清零、多实例各自计数）：{}",
                    cause.getMessage());
        }
        UserQuota quota = quotas.computeIfAbsent(userId, key -> new UserQuota());
        synchronized (quota) {
            if (!today.equals(quota.day)) {
                quota.day = today;
                quota.dailyCount = 0;
            }
            if (quota.dailyCount >= properties.getDailyLimit()) {
                throw new BusinessException(429, "今日 AI 使用次数已达上限");
            }
            if (!minuteLimiter.tryAcquire(userId, properties.getMinuteLimit(), MINUTE_WINDOW)) {
                throw new BusinessException(429, "操作过于频繁，请稍后再试");
            }
            quota.dailyCount++;
        }
    }

    /** 每用户每日计数，day 为计数归属日期（Asia/Shanghai）。字段只能在对应 quota 的监视器锁内访问。 */
    private static final class UserQuota {

        private LocalDate day = LocalDate.now(ZONE_ID);

        private int dailyCount;
    }
}

package com.hnist.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 读缓存装配：只收口「逐出时机可控」的热点数据（分类树、商品公开详情），
 * 列表类（帖子、商品列表）带点赞/计数等高频变化数据，逐出时机不可控，不进缓存。
 */
@Slf4j
@Configuration
@EnableCaching(order = 1)
public class CacheConfig implements CachingConfigurer {

    /** 缓存键统一前缀，与限流、黑名单等非缓存 key 隔离，便于按前缀批量排查。 */
    private static final String KEY_PREFIX = "cache:";

    private final RedisConnectionFactory connectionFactory;

    public CacheConfig(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * order=1 让缓存拦截器位于事务拦截器（默认最低优先级）外层：
     * 写方法 @CacheEvict 在事务提交之后才执行，避免「缓存已清、事务未提交，并发读把旧值回填」。
     */
    @Override
    public org.springframework.cache.CacheManager cacheManager() {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(RedisConfig.jsonRedisSerializer()))
                .computePrefixWith(name -> KEY_PREFIX + name + ":");

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                // 分类树几乎不变更（无管理端写接口），固定 TTL 兜底数据变更
                "categoryTree", defaults.entryTtl(Duration.ofMinutes(10)),
                // 商品详情会因商家改价/上下架失效，TTL 取短 + 写方法主动逐出
                "productDetail", defaults.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    /**
     * 缓存是旁路加速，任何 Redis 故障都不能阻断主链路：
     * 读取失败视为未命中（回落数据库），写入/逐出失败只记录不抛出。
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new ThrottledLoggingCacheErrorHandler();
    }

    /** Redis 故障期间的 WARN 限频：高频请求下每 30 秒最多一条，避免日志刷屏。 */
    private static final class ThrottledLoggingCacheErrorHandler implements CacheErrorHandler {

        private static final long LOG_INTERVAL_MILLIS = 30_000L;

        private final AtomicLong lastLogAt = new AtomicLong();

        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            warnThrottled("读取", cache, key, exception);
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            warnThrottled("写入", cache, key, exception);
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            warnThrottled("逐出", cache, key, exception);
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            warnThrottled("清空", cache, "-", exception);
        }

        private void warnThrottled(String action, Cache cache, Object key, RuntimeException exception) {
            long now = System.currentTimeMillis();
            long last = lastLogAt.get();
            if (now - last >= LOG_INTERVAL_MILLIS && lastLogAt.compareAndSet(last, now)) {
                log.warn("Redis 缓存{}失败（已降级为直接访问数据库），cache={} key={} 原因：{}",
                        action, cache.getName(), key, exception.getMessage());
            }
        }
    }
}

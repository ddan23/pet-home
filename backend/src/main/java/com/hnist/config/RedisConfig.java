package com.hnist.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** Redis 接入配置：统一 key 用字符串、value 用 JSON 序列化，避免 JDK 序列化产生的二进制不可读数据。 */
@Configuration
public class RedisConfig {

    /** value 携带 @class 类型信息的 JSON 序列化器，写入的缓存内容可直接用 redis-cli 人工排查。 */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = jsonRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        // 连接工厂由 starter 按 spring.data.redis.* 装配，Lettuce 懒连接：Redis 不在线不阻塞启动
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 实体字段含 LocalDateTime，默认 ObjectMapper 不带 JSR310 模块会序列化失败；
     * 自定义 mapper 必须显式开启 default typing（NON_FINAL + @class），
     * 否则反序列化会退化成 LinkedHashMap 而不是原类型，缓存读出来就不可用。
     */
    static GenericJackson2JsonRedisSerializer jsonRedisSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // 日期序列化成 ISO 字符串，redis-cli 里可人工读
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}

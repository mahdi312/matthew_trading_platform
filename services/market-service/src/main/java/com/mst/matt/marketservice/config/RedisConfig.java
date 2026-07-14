package com.mst.matt.marketservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis template wiring for {@code market-service}.
 *
 * <p>Registers a single {@link RedisTemplate}{@code <String, Object>} with:</p>
 * <ul>
 *   <li>Key serialiser: {@link StringRedisSerializer} — human-readable keys
 *       (inspectable with {@code redis-cli keys 'ohlcv:*'}).</li>
 *   <li>Value serialiser: {@link GenericJackson2JsonRedisSerializer} — Jackson
 *       JSON with embedded {@code @class} field, so deserialisation works even
 *       if a consuming instance has a different classloader order.</li>
 * </ul>
 *
 * <p>Connection is provided by Spring Boot's
 * {@code spring-boot-autoconfigure} Lettuce auto-config, driven by
 * {@code spring.data.redis.*} in {@code application.yml}.</p>
 *
 * <p>For local dev: {@code docker run -p 6379:6379 redis:7-alpine}.</p>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.setDefaultSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}

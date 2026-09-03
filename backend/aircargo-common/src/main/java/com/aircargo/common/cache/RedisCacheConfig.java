package com.aircargo.common.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.time.Duration;

/**
 * Caché REDIS compartida entre réplicas (modo HA).
 *
 * Activación: {@code spring.cache.type=redis} (env: SPRING_CACHE_TYPE=redis) en
 * cualquier servicio — requiere Redis accesible vía {@code spring.data.redis.host/port}
 * (env: REDIS_HOST/REDIS_PORT, ver docker-compose.infrastructure.yml).
 *
 * TTL: {@code spring.cache.redis.time-to-live} (default 300s). Para conservar los
 * TTL por servicio al migrar de Caffeine: SPRING_CACHE_REDIS_TIME_TO_LIVE=60s (export),
 * 120s (load-planning), 600s (auth), etc.
 *
 * Valores serializados como JSON (GenericJackson2JsonRedisSerializer) — sin exigir
 * Serializable en los DTOs cacheados. Claves legibles: nombre-cache::id.
 */
@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${spring.cache.redis.time-to-live:300s}") Duration ttl) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        CacheManager redis = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
        // Misma protección type-safe que en Caffeine (ver TypeSafeCacheManager).
        return new TypeSafeCacheManager(redis);
    }
}

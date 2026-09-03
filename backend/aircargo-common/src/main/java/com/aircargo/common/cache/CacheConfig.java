package com.aircargo.common.cache;

import com.github.benmanes.caffeine.cache.CaffeineSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caché Caffeine compartida por todos los servicios (antes 9 clases CacheConfig duplicadas).
 *
 * La spec se lee de {@code spring.cache.caffeine.spec} (env: SPRING_CACHE_CAFFEINE_SPEC), lo que
 * permite ajustar tamaño/TTL sin recompilar.
 *
 * Selección de backend vía {@code spring.cache.type}:
 *  - caffeine (default): caché en proceso — correcto con 1 instancia por servicio.
 *  - redis: delega en {@link RedisCacheConfig} (caché compartida entre réplicas, HA).
 *  - none: no registra CacheManager aquí — Boot auto-configura NoOpCacheManager.
 *
 * No se registra en servicios sin caffeine en classpath (p. ej. el gateway reactivo,
 * que sí recibe spring-context-support vía data-redis): se exigen AMBAS clases.
 */
@Configuration
@EnableCaching
@ConditionalOnClass(name = {"org.springframework.cache.caffeine.CaffeineCacheManager",
                            "com.github.benmanes.caffeine.cache.AsyncCache"})
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${spring.cache.caffeine.spec:maximumSize=500,expireAfterWrite=300s}") String caffeineSpec) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeineSpec(CaffeineSpec.parse(caffeineSpec));
        // Envolver en cache type-safe: valida el tipo del valor al leer y trata como miss
        // cualquier valor de tipo inconsistente (evita ClassCastException por colisión de
        // tipos en una misma caché).
        return new TypeSafeCacheManager(manager);
    }
}

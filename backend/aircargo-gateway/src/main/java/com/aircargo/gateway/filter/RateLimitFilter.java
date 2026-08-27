package com.aircargo.gateway.filter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter por usuario (X-User-Email).
 *
 * Dos backends:
 *  - REDIS (app.gateway.rate-limit.use-redis=true): contador de ventana fija
 *    compartido entre réplicas del gateway (INCR + EXPIRE, ventana de 1 min).
 *    Requerido para HA con 2+ instancias. Fail-open si Redis no responde
 *    (el rate limit nunca debe tumbar el tráfico).
 *  - IN-MEMORY (default): resilience4j por instancia — correcto para 1 réplica.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final Map<String, RateLimiter> userLimiters = new ConcurrentHashMap<>();

    private final boolean enabled;
    private final int limitPerMinute;
    private final long timeoutMs;
    private final boolean useRedis;
    private final ReactiveStringRedisTemplate redis;

    public RateLimitFilter(
            @Value("${app.gateway.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.gateway.rate-limit.limit-per-minute:100}") int limitPerMinute,
            @Value("${app.gateway.rate-limit.timeout-ms:50}") long timeoutMs,
            @Value("${app.gateway.rate-limit.use-redis:false}") boolean useRedis,
            ObjectProvider<ReactiveStringRedisTemplate> redisTemplate) {
        this.enabled = enabled;
        this.limitPerMinute = limitPerMinute;
        this.timeoutMs = timeoutMs;
        this.useRedis = useRedis;
        this.redis = useRedis ? redisTemplate.getIfAvailable() : null;
        if (useRedis && this.redis == null) {
            log.warn("rate-limit.use-redis=true pero no hay ReactiveStringRedisTemplate — se usa el límite en memoria");
        }
    }

    private RateLimiterConfig defaultConfig() {
        return RateLimiterConfig.custom()
                .limitForPeriod(limitPerMinute)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String email = request.getHeaders().getFirst("X-User-Email");
        if (email == null || email.isBlank()) {
            return chain.filter(exchange);
        }

        if (redis != null) {
            return checkRedis(email)
                    .flatMap(allowed -> allowed
                            ? chain.filter(exchange)
                            : tooManyRequests(exchange));
        }

        RateLimiter rateLimiter = userLimiters.computeIfAbsent(email, k ->
                RateLimiter.of("rl_" + k, defaultConfig()));

        if (rateLimiter.acquirePermission()) {
            return chain.filter(exchange);
        }
        return tooManyRequests(exchange);
    }

    /** Ventana fija de 1 minuto compartida en Redis: rl:{email}:{epochMinute}. */
    private Mono<Boolean> checkRedis(String email) {
        String window = Long.toString(Instant.now().getEpochSecond() / 60);
        String key = "rl:" + email + ":" + window;
        return redis.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count != null && count == 1L) {
                        // TTL holgado (70s) para que la ventana expire sola aunque nadie vuelva a tocar la key
                        return redis.expire(key, Duration.ofSeconds(70)).thenReturn(count);
                    }
                    return Mono.justOrEmpty(count);
                })
                .map(count -> count <= limitPerMinute)
                .onErrorResume(e -> {
                    log.warn("Rate limit Redis no disponible (fail-open): {}", e.getMessage());
                    return Mono.just(Boolean.TRUE);
                });
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        log.warn("Rate limit exceeded for user: {}",
                exchange.getRequest().getHeaders().getFirst("X-User-Email"));
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json");
        response.getHeaders().add("Retry-After", "60");
        String body = "{\"error\":\"Rate limit exceeded. Try again later.\",\"status\":429}";
        byte[] bytes = body.getBytes();
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -90;
    }
}

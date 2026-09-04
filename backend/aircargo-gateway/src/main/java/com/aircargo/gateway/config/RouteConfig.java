package com.aircargo.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class RouteConfig {

    private final Environment env;

    public RouteConfig(Environment env) {
        this.env = env;
    }

    private String svc(String key, String defaultUri) {
        return env.getProperty(key, defaultUri);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                .route("auth-service", r -> r
                        .path("/api/auth/**", "/api/users/**", "/api/audit-logs/**",
                             "/api/sites/**", "/api/role-permissions/**",
                             "/api/commodity-types/**", "/api/backup/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("auth-service")
                                        .setFallbackUri("forward:/fallback/auth"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setBackoff(java.time.Duration.ofMillis(500),
                                                     java.time.Duration.ofSeconds(2), 2, true))
                        )
                        .uri(svc("SERVICE_AUTH_URL", "http://localhost:9092")))

                .route("flight-service", r -> r
                        .path("/api/flights/**", "/api/airlines/**", "/api/aircraft-types/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("flight-service")
                                        .setFallbackUri("forward:/fallback/flight"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setBackoff(java.time.Duration.ofMillis(500),
                                                     java.time.Duration.ofSeconds(2), 2, true))
                        )
                        .uri(svc("SERVICE_FLIGHT_URL", "http://localhost:9093")))

                .route("booking-service", r -> r
                        .path("/api/bookings/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("booking-service")
                                        .setFallbackUri("forward:/fallback/booking"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setBackoff(java.time.Duration.ofMillis(500),
                                                     java.time.Duration.ofSeconds(2), 2, true))
                        )
                        .uri(svc("SERVICE_BOOKING_URL", "http://localhost:9094")))

                .route("mawb-service", r -> r
.path("/api/mawbs/**", "/api/hawbs/**",
     "/api/cargo/mawbs/**", "/api/cargo/hawbs/**",
     "/api/tracking/**", "/api/compliance/**",
     "/api/label-templates/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("mawb-service")
                                        .setFallbackUri("forward:/fallback/mawb"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setBackoff(java.time.Duration.ofMillis(500),
                                                     java.time.Duration.ofSeconds(2), 2, true))
                                .rewritePath("/api/cargo/mawbs/(?<seg>.*)", "/api/mawbs/${seg}")
                                .rewritePath("/api/cargo/mawbs$", "/api/mawbs")
                                .rewritePath("/api/cargo/hawbs/(?<seg>.*)", "/api/hawbs/${seg}")
                                .rewritePath("/api/cargo/hawbs$", "/api/hawbs")
                        )
                        .uri(svc("SERVICE_MAWB_URL", "http://localhost:9095")))

                .route("warehouse-service", r -> r
                        .path("/api/warehouse/**", "/api/receipts/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("warehouse-service")
                                        .setFallbackUri("forward:/fallback/warehouse"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setBackoff(java.time.Duration.ofMillis(500),
                                                     java.time.Duration.ofSeconds(2), 2, true))
                        )
                        .uri(svc("SERVICE_WAREHOUSE_URL", "http://localhost:9096")))

                .route("uld-service", r -> r
                        .path("/api/ulds/**", "/api/uld-awbs/**",
                             "/api/uld-type-config/**", "/api/uld-type-catalog/**", "/api/scan/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("uld-service")
                                        .setFallbackUri("forward:/fallback/uld"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setBackoff(java.time.Duration.ofMillis(500),
                                                     java.time.Duration.ofSeconds(2), 2, true))
                        )
                        .uri(svc("SERVICE_ULD_URL", "http://localhost:9097")))

                .route("load-planning-service", r -> r
                        .path("/api/load-planning/**", "/api/cargo/flights/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("load-planning-service")
                                        .setFallbackUri("forward:/fallback/load-planning"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setBackoff(java.time.Duration.ofMillis(500),
                                                     java.time.Duration.ofSeconds(2), 2, true))
                        )
                        .uri(svc("SERVICE_LOAD_PLANNING_URL", "http://localhost:9098")))

                .route("export-service", r -> r
                        .path("/api/exports/**", "/api/bi/**", "/api/reports/**", "/api/catalog/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("export-service")
                                        .setFallbackUri("forward:/fallback/export"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setBackoff(java.time.Duration.ofMillis(500),
                                                     java.time.Duration.ofSeconds(2), 2, true))
                        )
                        .uri(svc("SERVICE_EXPORT_URL", "http://localhost:9099")))

                .route("notification-service", r -> r
                        .path("/api/notifications/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("notification-service")
                                        .setFallbackUri("forward:/fallback/notification"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setBackoff(java.time.Duration.ofMillis(500),
                                                     java.time.Duration.ofSeconds(2), 2, true))
                        )
                        .uri(svc("SERVICE_NOTIFICATION_URL", "http://localhost:9100")))

                // ─── Swagger API docs routes ────────────────────────
                .route("auth-api-docs", r -> r.path("/auth/api-docs/**").uri(svc("SERVICE_AUTH_URL", "http://localhost:9092")))
                .route("flight-api-docs", r -> r.path("/flight/api-docs/**").uri(svc("SERVICE_FLIGHT_URL", "http://localhost:9093")))
                .route("booking-api-docs", r -> r.path("/booking/api-docs/**").uri(svc("SERVICE_BOOKING_URL", "http://localhost:9094")))
                .route("mawb-api-docs", r -> r.path("/mawb/api-docs/**").uri(svc("SERVICE_MAWB_URL", "http://localhost:9095")))
                .route("warehouse-api-docs", r -> r.path("/warehouse/api-docs/**").uri(svc("SERVICE_WAREHOUSE_URL", "http://localhost:9096")))
                .route("uld-api-docs", r -> r.path("/uld/api-docs/**").uri(svc("SERVICE_ULD_URL", "http://localhost:9097")))
                .route("load-planning-api-docs", r -> r.path("/load-planning/api-docs/**").uri(svc("SERVICE_LOAD_PLANNING_URL", "http://localhost:9098")))
                .route("export-api-docs", r -> r.path("/export/api-docs/**").uri(svc("SERVICE_EXPORT_URL", "http://localhost:9099")))
                .route("notification-api-docs", r -> r.path("/notification/api-docs/**").uri(svc("SERVICE_NOTIFICATION_URL", "http://localhost:9100")))

                .build();
    }
}

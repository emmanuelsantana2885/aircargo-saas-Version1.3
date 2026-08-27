package com.aircargo.common.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final long REVOCATION_CACHE_MS = 30_000;
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login", "/api/auth/set-password", "/api/auth/set-password-token",
            "/api/auth/reset-password/", "/api/auth/refresh", "/api/auth/heartbeat"
    );

    private final JwtUtil jwtUtil;
    // Opcional: habilita revocación central por-request (tokens_valid_from / blocked / is_active)
    private final JdbcTemplate jdbcTemplate;
    private final Map<UUID, UserState> stateCache = new ConcurrentHashMap<>();

    private record UserState(boolean blocked, boolean active, OffsetDateTime tokensValidFrom, long loadedAtMs) {}

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this(jwtUtil, null);
    }

    public JwtAuthFilter(JwtUtil jwtUtil, JdbcTemplate jdbcTemplate) {
        this.jwtUtil = jwtUtil;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Endpoints públicos: skip JWT validation (permitAll en SecurityConfig se encarga)
        if (PUBLIC_PATHS.stream().anyMatch(uri::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            log.debug("NO token for {} {}", method, uri);
            chain.doFilter(request, response);
            return;
        }

        try {
            if (jwtUtil.isRevoked(token)) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response, "Token revoked");
                return;
            }
            Claims claims = jwtUtil.parseToken(token);
            String role = claims.get("role", String.class);
            if (role == null) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response, "Invalid token (no role)");
                return;
            }
            String userId = claims.getSubject();
            String airlineId = claims.get("airlineId", String.class);
            String email = claims.get("email", String.class);
            String fullName = claims.get("fullName", String.class);

            // Revocación central por-request (solo servicios con BD; caché 30s)
            if (jdbcTemplate != null && isStale(UUID.fromString(userId), claims)) {
                log.info("Session REVOKED for {} {}", method, uri);
                SecurityContextHolder.clearContext();
                writeUnauthorized(response, "Session revoked");
                return;
            }

            UserPrincipal principal = new UserPrincipal(userId, role, airlineId, email, fullName);
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            log.info("JWT INVALID for {} {}: {}", method, uri, e.getMessage());
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, "Token expired or invalid");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Bearer header tiene prioridad; fallback a cookie httpOnly. */
    static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return CookieAuthSupport.extractToken(request, CookieAuthSupport.ACCESS_COOKIE);
    }

    private boolean isStale(UUID userId, Claims claims) {
        UserState s = stateCache.compute(userId, (k, prev) -> {
            if (prev != null && System.currentTimeMillis() - prev.loadedAtMs() < REVOCATION_CACHE_MS) {
                return prev;
            }
            try {
                return jdbcTemplate.queryForObject(
                        "SELECT blocked, is_active, COALESCE(tokens_valid_from, TIMESTAMP '1970-01-01 00:00:00+00') FROM app_user WHERE id = ?",
                        (rs, n) -> new UserState(rs.getBoolean(1), rs.getBoolean(2),
                                rs.getObject(3, OffsetDateTime.class), System.currentTimeMillis()),
                        userId);
            } catch (Exception e) {
                log.warn("No se pudo leer estado de usuario {}: {}", userId, e.getMessage());
                return prev != null ? prev : new UserState(false, true, null, System.currentTimeMillis());
            }
        });
        if (s.blocked() || !s.active()) return true;
        java.util.Date iat = claims.getIssuedAt();
        return iat != null && s.tokensValidFrom() != null
                && OffsetDateTime.ofInstant(iat.toInstant(), java.time.ZoneOffset.UTC).isBefore(s.tokensValidFrom());
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        response.getWriter().flush();
    }
}

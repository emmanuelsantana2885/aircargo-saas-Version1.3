package com.aircargo.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final String DEV_DEFAULT = "dev-only-insecure-secret-do-not-use-in-production-please-change-me";
    private static final long ACCESS_TOKEN_MS = 15 * 60 * 1000L;       // 15 minutes
    private static final long ENROLL_TOKEN_MS = 15 * 60 * 1000L;       // 15 minutes
    private static final long REFRESH_TOKEN_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days
    private static final long CLEANUP_INTERVAL_MS = 60 * 60 * 1000L;   // 1 hour

    private final SecretKey key;
    private final long expirationMs;
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();
    private volatile long lastCleanupMs = System.currentTimeMillis();

    public JwtUtil(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${app.jwt.allow-dev-secret:false}") boolean allowDevSecret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET environment variable is not set. " +
                "Generate one with: openssl rand -base64 64");
        }
        if (DEV_DEFAULT.equals(secret)) {
            if (allowDevSecret) {
                log.warn("⚠ Using INSECURE dev-only JWT secret. Set JWT_SECRET env var for production!");
            } else {
                throw new IllegalStateException(
                    "app.jwt.secret is set to the INSECURE dev default. " +
                    "Set the JWT_SECRET environment variable (openssl rand -base64 64), " +
                    "or set app.jwt.allow-dev-secret=true for local development only.");
            }
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String userId, String role, String airlineId, String email, String fullName) {
        return generateAccessToken(userId, role, airlineId, email, fullName);
    }

    public String generateAccessToken(String userId, String role, String airlineId, String email, String fullName) {
        Date now = new Date();
        long ttlMs = expirationMs > 0 ? expirationMs : ACCESS_TOKEN_MS;
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .claim("airlineId", airlineId)
                .claim("email", email)
                .claim("fullName", fullName != null ? fullName : "")
                .claim("tokenType", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    /**
     * Token de corta duración que solo autoriza el flujo de enrolamiento MFA
     * (setup + enable). Se emite en el login cuando MFA es obligatorio pero el
     * usuario aún no lo tiene configurado. Nunca da acceso a APIs de negocio.
     */
    public String generateEnrollToken(String userId, String role, String email, String fullName) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .id(java.util.UUID.randomUUID().toString())
                .claim("role", role)
                .claim("email", email)
                .claim("fullName", fullName != null ? fullName : "")
                .claim("tokenType", "enroll")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ENROLL_TOKEN_MS))
                .signWith(key)
                .compact();
    }

    private static final long SERVICE_TOKEN_MS = 365L * 24 * 60 * 60 * 1000; // 365 days

    public String generateServiceToken(String userId, String role, String airlineId, String email, String fullName) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .claim("airlineId", airlineId)
                .claim("email", email)
                .claim("fullName", fullName != null ? fullName : "")
                .claim("tokenType", "service")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + SERVICE_TOKEN_MS))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("tokenType", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TOKEN_MS))
                .signWith(key)
                .compact();
    }

    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("tokenType", String.class);
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Token Revocation ──────────────────────────────────────

    /** Fecha de emisión (iat) del token; null si es inválido o no trae iat. */
    public java.time.OffsetDateTime getIssuedAt(String token) {
        try {
            java.util.Date iat = parseToken(token).getIssuedAt();
            return iat != null ? java.time.OffsetDateTime.ofInstant(iat.toInstant(), java.time.ZoneOffset.UTC) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void revokeToken(String token) {
        revokedTokens.add(token);
        maybeCleanup();
    }

    public boolean isRevoked(String token) {
        maybeCleanup();
        return revokedTokens.contains(token);
    }

    private synchronized void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupMs < CLEANUP_INTERVAL_MS) return;
        lastCleanupMs = now;
        long before = revokedTokens.size();
        revokedTokens.removeIf(token -> {
            try {
                return parseToken(token).getExpiration().before(new Date());
            } catch (Exception e) {
                return true;
            }
        });
        if (revokedTokens.size() != before) {
            log.debug("Revocation blacklist cleaned: {} → {} entries", before, revokedTokens.size());
        }
    }
}

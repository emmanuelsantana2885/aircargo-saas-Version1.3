package com.aircargo.authservice.service;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Revocación central de tokens.
 *
 * · bump(userId): adelanta tokens_valid_from → TODO token emitido antes
 *   (access y refresh) deja de ser válido. Se invoca en eventos sensibles:
 *   bloqueo, desactivación, reset/cambio de contraseña, enable/disable MFA.
 * · isStale(user, issuedAt): true si el token fue emitido antes de la
 *   fecha de revocación del usuario.
 *
 * La lectura para el filtro por-request se memoiza 30s (PK lookup barato,
 * ventana máxima de gracia tras un bump).
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);
    private static final long CACHE_MS = 30_000;

    private final AppUserRepository userRepository;
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(OffsetDateTime validFrom, long loadedAtMs) {}

    public TokenRevocationService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @org.springframework.transaction.annotation.Transactional
    public void bump(UUID userId) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setTokensValidFrom(OffsetDateTime.now());
            userRepository.save(u);
            cache.remove(userId);
            log.info("Tokens revocados centralmente para usuario {}", userId);
        });
    }

    /** null user → no revocado (el flujo llamador decide); compara iat del token. */
    public boolean isStale(UUID userId, OffsetDateTime issuedAt) {
        if (userId == null || issuedAt == null) return false;
        OffsetDateTime validFrom = cachedValidFrom(userId);
        return validFrom != null && issuedAt.isBefore(validFrom);
    }

    public void evict(UUID userId) {
        cache.remove(userId);
    }

    private OffsetDateTime cachedValidFrom(UUID userId) {
        CacheEntry e = cache.get(userId);
        long now = System.currentTimeMillis();
        if (e != null && now - e.loadedAtMs() < CACHE_MS) return e.validFrom();
        return userRepository.findById(userId)
                .map(u -> u.getTokensValidFrom())
                .map(vf -> {
                    cache.put(userId, new CacheEntry(vf, now));
                    return vf;
                })
                .orElseGet(() -> {
                    // usuario inexistente: cachear "sin restricción" brevemente; el flujo llamador lo trata como 401 igualmente
                    cache.put(userId, new CacheEntry(null, now));
                    return null;
                });
    }
}

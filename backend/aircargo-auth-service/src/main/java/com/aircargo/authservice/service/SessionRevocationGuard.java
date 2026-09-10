package com.aircargo.authservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Detecta sesiones cuyo navegador se cerró (heartbeat detenido) y las revoca
 * de forma central: adelanta tokens_valid_from → todos los tokens de ese
 * usuario (access y refresh), incluidas cookies restauradas por navegadores
 * con "continuar donde lo dejé", dejan de ser válidos en TODOS los servicios
 * (chequeo por-request del JwtAuthFilter contra tokens_valid_from).
 */
@Component
public class SessionRevocationGuard {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationGuard.class);

    private final ActiveSessionTracker sessionTracker;
    private final TokenRevocationService tokenRevocationService;

    public SessionRevocationGuard(ActiveSessionTracker sessionTracker,
                                  TokenRevocationService tokenRevocationService) {
        this.sessionTracker = sessionTracker;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Scheduled(fixedRate = 60_000)
    public void revokeClosedBrowserSessions() {
        List<UUID> stalled = sessionTracker.staleUserIds();
        for (UUID userId : stalled) {
            try {
                tokenRevocationService.bump(userId);
                log.info("Sesión revocada por cierre de navegador (heartbeat detenido): {}", userId);
            } catch (Exception e) {
                log.warn("Error al revocar sesión por cierre de navegador {}: {}", userId, e.getMessage());
            }
        }
        sessionTracker.purgeStaleSessions();
    }
}
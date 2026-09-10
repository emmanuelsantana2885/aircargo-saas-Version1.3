package com.aircargo.authservice.service;

import com.aircargo.authservice.dto.ConnectedUserDTO;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActiveSessionTracker {

    /**
     * Ventana de vida de una sesión para el/la refresh: si el navegador no
     * envía heartbeat en este lapso, se considera que el navegador se cerró
     * y el refresh deja de ser válido (la app pide login de nuevo).
     * El frontend late cada 30s mientras la app está abierta, así que cualquier
     * pestaña abierta mantiene la sesión viva. Al CERRAR el navegador (todas
     * las pestañas), los heartbeats se detienen → 120s después el refresh
     * falla aunque el navegador esté configurado para restaurar cookies.
     */
    private static final long LIVENESS_SECONDS = 120;

    /**
     * Umbral de REVOCACIÓN server-side (access + refresh): cuando un usuario
     * supera este lapso sin heartbeat, su tokens_valid_from se adelanta y TODOS
     * sus tokens (incluidas cookies restauradas por el navegador) mueren de
     * forma central en todos los servicios. 300s (conservador) para no
     * revocar a pestañas ocultas throttled por el navegador.
     */
    private static final long REVOKE_TIMEOUT_SECONDS = 300;

    private final long livenessSeconds;
    private final long revokeTimeoutSeconds;
    private final Map<UUID, HeartbeatEntry> sessions = new ConcurrentHashMap<>();

    public ActiveSessionTracker() {
        this(LIVENESS_SECONDS, REVOKE_TIMEOUT_SECONDS);
    }

    /** Constructor de tests: permite ventanas cortas sin esperar 120s/300s. */
    ActiveSessionTracker(long livenessSeconds, long revokeTimeoutSeconds) {
        this.livenessSeconds = livenessSeconds;
        this.revokeTimeoutSeconds = revokeTimeoutSeconds;
    }

    public void recordHeartbeat(UUID userId, String email, String fullName, String role, OffsetDateTime lastLogin) {
        sessions.put(userId, new HeartbeatEntry(userId, email, fullName, role, OffsetDateTime.now(), lastLogin));
    }

    public void removeSession(UUID userId) {
        sessions.remove(userId);
    }

    /** true si el navegador aún envía heartbeat (pestaña abierta). */
    public boolean isAlive(UUID userId) {
        if (userId == null) return false;
        HeartbeatEntry e = sessions.get(userId);
        return e != null && e.lastHeartbeat.isAfter(OffsetDateTime.now().minusSeconds(livenessSeconds));
    }

    public List<ConnectedUserDTO> getConnectedUsers() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(livenessSeconds);
        return sessions.values().stream()
                .filter(e -> e.lastHeartbeat.isAfter(cutoff))
                .map(e -> ConnectedUserDTO.builder()
                        .userId(e.userId)
                        .email(e.email)
                        .fullName(e.fullName)
                        .role(e.role)
                        .lastHeartbeat(e.lastHeartbeat)
                        .lastLogin(e.lastLogin)
                        .build())
                .toList();
    }

    /** Sesiones sin heartbeat en el umbral de revocación (para SessionRevocationGuard). */
    public List<UUID> staleUserIds() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(revokeTimeoutSeconds);
        return sessions.values().stream()
                .filter(e -> e.lastHeartbeat.isBefore(cutoff))
                .map(e -> e.userId)
                .toList();
    }

    /** Elimina las sesiones revocadas; devuelve los userId removidos. */
    public List<UUID> purgeStaleSessions() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(revokeTimeoutSeconds);
        List<UUID> removed = sessions.values().stream()
                .filter(e -> e.lastHeartbeat.isBefore(cutoff))
                .map(e -> e.userId)
                .toList();
        sessions.values().removeIf(e -> e.lastHeartbeat.isBefore(cutoff));
        return removed;
    }

    private record HeartbeatEntry(UUID userId, String email, String fullName, String role,
                                  OffsetDateTime lastHeartbeat, OffsetDateTime lastLogin) {}
}

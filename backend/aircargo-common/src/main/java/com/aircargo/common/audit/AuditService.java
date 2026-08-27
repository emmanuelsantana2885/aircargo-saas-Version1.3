package com.aircargo.common.audit;

import com.aircargo.common.util.IpAnonymizer;
import com.aircargo.common.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Auditoría compartida entre servicios — FUENTE ÚNICA DE VERDAD.
 *
 * Estrategia de escritura (Mayor B — cero pérdida sin broker):
 * INSERT directo en la tabla compartida {@code audit_log} — el query-side
 * de auth-service ya la lee, así que el evento es visible inmediatamente
 * en Seguridad, haya o no RabbitMQ.
 *
 * NOTA: antes existía también un publish AMQP "audit.log" consumido por
 * notification-service (copia en notification.audit_log) — eliminado por
 * registrar los eventos DOS veces. La tabla compartida es el único destino.
 *
 * Bean name explícito para no colisionar con el AuditService local de auth-service.
 */
@Service("sharedAuditService")
@ConditionalOnClass(JdbcTemplate.class)
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final String INSERT_SQL =
            "INSERT INTO audit_log (id, user_id, email, full_name, action, entity_type, entity_id, details, ip_address, created_at) "
          + "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, now())";

    private final Optional<JdbcTemplate> jdbcTemplate;

    public AuditService(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Optional.ofNullable(jdbcTemplate);
        if (jdbcTemplate == null) {
            log.warn("AuditService sin JdbcTemplate: los eventos NO pueden persistirse (se descartan con log)");
        }
    }

    public void log(UUID userId, String email, String fullName, String action,
                    String entityType, String entityId, String details, String ipAddress) {
        writeLocal(userId, email, fullName, action, entityType, entityId, details,
                IpAnonymizer.truncate(ipAddress));
    }

    /** PRIMARY y única vía: persistencia directa en la BD compartida. */
    private void writeLocal(UUID userId, String email, String fullName, String action,
                            String entityType, String entityId, String details, String safeIp) {
        boolean persisted = jdbcTemplate.map(jdbc -> {
            try {
                jdbc.update(INSERT_SQL, userId, email, fullName, action,
                        entityType, entityId, TextUtil.safe(details), safeIp);
                return true;
            } catch (Exception e) {
                log.error("Audit INSERT falló: {}", e.getMessage());
                return false;
            }
        }).orElse(false);
        if (!persisted) {
            // sin BD no hay dónde registrar el evento: dejar rastro en el log local
            log.warn("Auditoría NO persistida | {} | {} | {}", action, email, safeIp);
        }
    }

    public void logLogin(UUID userId, String email, String fullName, String ipAddress) {
        log(userId, email, fullName, "LOGIN", null, null, null, ipAddress);
    }
}

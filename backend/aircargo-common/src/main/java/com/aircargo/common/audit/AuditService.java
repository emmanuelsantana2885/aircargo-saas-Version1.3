package com.aircargo.common.audit;

import com.aircargo.common.event.AuditLogEvent;
import com.aircargo.common.util.IpAnonymizer;
import com.aircargo.common.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Auditoría compartida entre servicios.
 *
 * Estrategia de escritura (Mayor B — cero pérdida sin broker):
 * 1. PRIMARY: INSERT directo en la tabla compartida {@code audit_log} — el
 *    query-side de auth-service ya la lee, así que el evento es visible
 *    inmediatamente en Seguridad, haya o no RabbitMQ.
 * 2. SECUNDARIO (best-effort): publish AMQP "audit.log" para consumidores
 *    en tiempo real; un fallo aquí NO afecta la integridad del registro.
 *
 * Solo se registra en servicios con spring-boot-starter-amqp en classpath
 * ({@link ConditionalOnClass}) y JdbcTemplate disponible (datasource).
 */
@Service
@ConditionalOnClass(RabbitTemplate.class)
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final String AUDIT_ROUTING_KEY = "audit.log";
    private static final String INSERT_SQL =
            "INSERT INTO audit_log (id, user_id, email, full_name, action, entity_type, entity_id, details, ip_address, created_at) "
          + "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, now())";

    private final Optional<RabbitTemplate> rabbitTemplate;
    private final Optional<JdbcTemplate> jdbcTemplate;

    @Value("${rabbitmq.exchange:aircargo.events}")
    private String exchange;

    public AuditService(Optional<RabbitTemplate> rabbitTemplate,
                        @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.jdbcTemplate = Optional.ofNullable(jdbcTemplate);
        if (jdbcTemplate == null) {
            log.warn("AuditService sin JdbcTemplate: los eventos solo irán por AMQP (pueden perderse sin broker)");
        }
    }

    public void log(UUID userId, String email, String fullName, String action,
                    String entityType, String entityId, String details, String ipAddress) {
        String safeIp = IpAnonymizer.truncate(ipAddress);
        boolean persisted = writeLocal(userId, email, fullName, action, entityType, entityId, details, safeIp);
        if (!persisted) {
            // sin BD local: intentar broker como único medio de persistencia
            publish(userId, email, fullName, action, entityType, entityId, details, safeIp);
        } else {
            // best-effort para consumidores en tiempo real
            publish(userId, email, fullName, action, entityType, entityId, details, safeIp);
        }
    }

    /** PRIMARY: persistencia directa en la BD compartida. @return true si escribió */
    private boolean writeLocal(UUID userId, String email, String fullName, String action,
                               String entityType, String entityId, String details, String safeIp) {
        return jdbcTemplate.map(jdbc -> {
            try {
                jdbc.update(INSERT_SQL, userId, email, fullName, action,
                        entityType, entityId, TextUtil.safe(details), safeIp);
                return true;
            } catch (Exception e) {
                log.error("Audit fallback local falló: {}", e.getMessage());
                return false;
            }
        }).orElse(false);
    }

    /** SECUNDARIO: best-effort, nunca lanza. */
    private void publish(UUID userId, String email, String fullName, String action,
                         String entityType, String entityId, String details, String safeIp) {
        rabbitTemplate.ifPresent(rt -> {
            try {
                rt.convertAndSend(exchange, AUDIT_ROUTING_KEY,
                        new AuditLogEvent(userId, email, fullName, action, entityType,
                                entityId, TextUtil.safe(details), safeIp));
            } catch (Exception e) {
                log.debug("Audit publish no disponible (registro ya persistido en audit_log): {}", e.getMessage());
            }
        });
    }

    public void logLogin(UUID userId, String email, String fullName, String ipAddress) {
        log(userId, email, fullName, "LOGIN", null, null, null, ipAddress);
    }
}

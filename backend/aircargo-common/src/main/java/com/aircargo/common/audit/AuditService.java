package com.aircargo.common.audit;

import com.aircargo.common.event.AuditLogEvent;
import com.aircargo.common.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Auditoría compartida entre servicios.
 *
 * Publica un {@link AuditLogEvent} en el exchange de RabbitMQ (routing key "audit.log").
 * El consumidor es el notification-service, que lo persiste de forma durable.
 * Nunca bloquea el flujo de negocio: si RabbitMQ no está disponible, el fallo se
 * loguea y se ignora (fire-and-forget).
 *
 * Solo se registra en servicios que tienen spring-boot-starter-amqp en su classpath
 * ({@link ConditionalOnClass}). Para servicios sin RabbitMQ se debe excluir
 * RabbitAutoConfiguration y el bean Optional&lt;RabbitTemplate&gt; quedará vacío.
 */
@Service
@ConditionalOnClass(RabbitTemplate.class)
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final String AUDIT_ROUTING_KEY = "audit.log";

    private final Optional<RabbitTemplate> rabbitTemplate;

    @Value("${rabbitmq.exchange:aircargo.events}")
    private String exchange;

    public AuditService(Optional<RabbitTemplate> rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void log(UUID userId, String email, String fullName, String action,
                    String entityType, String entityId, String details, String ipAddress) {
        rabbitTemplate.ifPresent(rt -> {
            try {
                rt.convertAndSend(exchange, AUDIT_ROUTING_KEY,
                        new AuditLogEvent(userId, email, fullName, action, entityType,
                                entityId, TextUtil.safe(details), ipAddress));
            } catch (Exception e) {
                log.warn("Audit publish failed (non-blocking): {}", e.getMessage());
            }
        });
    }

    public void logLogin(UUID userId, String email, String fullName, String ipAddress) {
        log(userId, email, fullName, "LOGIN", null, null, null, ipAddress);
    }
}

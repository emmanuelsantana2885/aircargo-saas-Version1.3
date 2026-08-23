package com.aircargo.authservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Write side of the audit event store (event sourcing). The only way to
 * persist an {@link AuditEvent}. Appends join the caller's transaction so an
 * event is committed atomically with the state change that caused it; a failed
 * audit write is logged and never propagates to break the business operation.
 */
@Service
public class AuditEventStore {

    private static final Logger log = LoggerFactory.getLogger(AuditEventStore.class);

    private final AuditEventRepository repository;

    public AuditEventStore(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void append(UUID userId, String email, String fullName, String eventType,
                       String entityType, String entityId, String payload, String ipAddress) {
        try {
            repository.save(AuditEvent.builder()
                    .userId(userId)
                    .email(email)
                    .fullName(fullName)
                    .eventType(eventType)
                    .entityType(entityType)
                    .entityId(entityId)
                    .payload(payload)
                    .ipAddress(com.aircargo.common.util.IpAnonymizer.truncate(ipAddress))
                    .build());
        } catch (Exception e) {
            log.error("Failed to append audit event {} for user {}: {}", eventType, email, e.getMessage());
        }
    }
}

package com.aircargo.authservice.event;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only audit event (event sourcing). Events are facts that already
 * happened: they are never updated or deleted. Reads go through
 * {@link com.aircargo.authservice.query.AuditQueryService}.
 */
@Entity
@Table(name = "audit_event", indexes = {
        @Index(name = "idx_audit_event_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_event_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_event_entity", columnList = "entity_type, entity_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private String eventType;

    @Column(name = "entity_type", length = 50, updatable = false)
    private String entityType;

    @Column(name = "entity_id", length = 50, updatable = false)
    private String entityId;

    /** JSON payload with event-specific data (attemptCount, lockedUntil, changes...). */
    @Column(name = "payload", columnDefinition = "text", updatable = false)
    private String payload;

    @Column(name = "ip_address", length = 50, updatable = false)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}

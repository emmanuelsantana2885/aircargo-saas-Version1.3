package com.aircargo.authservice.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Read access to the append-only event store. There is deliberately no
 * delete/update API here: events are immutable facts.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AuditEvent> findAllByOrderByCreatedAtDesc();

    List<AuditEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);

    @Query("SELECT e FROM AuditEvent e WHERE e.eventType IN :types OR e.entityType = :entityType ORDER BY e.createdAt DESC")
    List<AuditEvent> findSecurityEvents(@Param("types") List<String> types, @Param("entityType") String entityType);
}

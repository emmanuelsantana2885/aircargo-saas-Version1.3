package com.aircargo.authservice.query;

import com.aircargo.authservice.dto.AuditLogDTO;
import com.aircargo.authservice.entity.AuditLog;
import com.aircargo.authservice.event.AuditEvent;
import com.aircargo.authservice.event.AuditEventRepository;
import com.aircargo.authservice.event.AuditEventType;
import com.aircargo.authservice.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Query side of the audit CQRS split. All audit reads are served from the
 * append-only event store; the frozen legacy {@code audit_log} table is merged
 * in so history written before the event-sourcing migration stays visible.
 */
@Service
public class AuditQueryService {

    private final AuditEventRepository eventRepository;
    private final AuditLogRepository legacyRepository;

    public AuditQueryService(AuditEventRepository eventRepository, AuditLogRepository legacyRepository) {
        this.eventRepository = eventRepository;
        this.legacyRepository = legacyRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> findByUser(UUID userId) {
        return merge(
                eventRepository.findByUserIdOrderByCreatedAtDesc(userId),
                legacyRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> findByAction(String action) {
        return merge(
                eventRepository.findByEventTypeOrderByCreatedAtDesc(action),
                legacyRepository.findByActionOrderByCreatedAtDesc(action));
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> findAll() {
        return merge(
                eventRepository.findAllByOrderByCreatedAtDesc(),
                legacyRepository.findAllByOrderByCreatedAtDesc());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> findByEntity(String entityType, String entityId) {
        List<AuditEvent> events = eventRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
        List<AuditLog> legacy = "USER".equalsIgnoreCase(entityType) || entityId == null
                ? new ArrayList<>()
                : filterLegacyByEntity(legacyRepository.findAllByOrderByCreatedAtDesc(), entityType, entityId);
        return merge(events, legacy);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> findSecurityEvents() {
        List<String> types = AuditEventType.SECURITY_ACTIONS;
        List<AuditEvent> events = eventRepository.findSecurityEvents(types, "USER");
        List<AuditLog> legacy = legacyRepository.findSecurityEvents(types, "USER");
        return merge(events, legacy).stream()
                .filter(dto -> dto.getAction() != null && (
                        types.contains(dto.getAction()) || "USER".equals(dto.getEntityType())))
                .toList();
    }

    private List<AuditLog> filterLegacyByEntity(List<AuditLog> all, String entityType, String entityId) {
        return all.stream()
                .filter(a -> entityType.equals(a.getEntityType())
                        && (entityId == null || entityId.equals(a.getEntityId())))
                .toList();
    }

    private List<AuditLogDTO> merge(List<AuditEvent> events, List<AuditLog> legacy) {
        List<AuditLogDTO> merged = new ArrayList<>(events.size() + legacy.size());
        for (AuditEvent e : events) {
            merged.add(AuditLogDTO.builder()
                    .id(e.getId())
                    .userId(e.getUserId())
                    .email(e.getEmail())
                    .fullName(e.getFullName())
                    .action(e.getEventType())
                    .entityType(e.getEntityType())
                    .entityId(e.getEntityId())
                    .details(e.getPayload())
                    .ipAddress(e.getIpAddress())
                    .createdAt(e.getCreatedAt())
                    .build());
        }
        for (AuditLog a : legacy) {
            merged.add(AuditLogDTO.fromEntity(a));
        }
        merged.sort(Comparator.comparing(AuditLogDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return merged;
    }
}

package com.aircargo.authservice.controller;

import com.aircargo.authservice.dto.AuditLogDTO;
import com.aircargo.authservice.dto.ConnectedUserDTO;
import com.aircargo.authservice.query.AuditQueryService;
import com.aircargo.authservice.service.ActiveSessionTracker;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Query endpoints over the audit event store (CQRS read side).
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditQueryService auditQueryService;
    private final ActiveSessionTracker sessionTracker;

    public AuditLogController(AuditQueryService auditQueryService, ActiveSessionTracker sessionTracker) {
        this.auditQueryService = auditQueryService;
        this.sessionTracker = sessionTracker;
    }

    @GetMapping
    public List<AuditLogDTO> getAll(@RequestParam(required = false) UUID userId,
                                    @RequestParam(required = false) String action,
                                    @RequestParam(required = false) String entityType,
                                    @RequestParam(required = false) String entityId) {
        if (userId != null) {
            return auditQueryService.findByUser(userId);
        }
        if (action != null) {
            return auditQueryService.findByAction(action);
        }
        if (entityType != null) {
            return auditQueryService.findByEntity(entityType, entityId);
        }
        return auditQueryService.findAll();
    }

    @GetMapping("/security")
    public List<AuditLogDTO> getSecurityEvents() {
        return auditQueryService.findSecurityEvents();
    }

    @GetMapping("/connected")
    public List<ConnectedUserDTO> getConnectedUsers() {
        return sessionTracker.getConnectedUsers();
    }
}

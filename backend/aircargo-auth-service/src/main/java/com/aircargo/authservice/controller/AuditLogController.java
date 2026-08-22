package com.aircargo.authservice.controller;

import com.aircargo.authservice.entity.AuditLog;
import com.aircargo.authservice.repository.AuditLogRepository;
import com.aircargo.authservice.service.ActiveSessionTracker;
import com.aircargo.authservice.dto.ConnectedUserDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogRepository repository;
    private final ActiveSessionTracker sessionTracker;

    public AuditLogController(AuditLogRepository repository, ActiveSessionTracker sessionTracker) {
        this.repository = repository;
        this.sessionTracker = sessionTracker;
    }

    @GetMapping
    public List<AuditLog> getAll(@RequestParam(required = false) UUID userId,
                                  @RequestParam(required = false) String action,
                                  @RequestParam(required = false) String entityType) {
        if (userId != null) {
            return repository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        if (action != null) {
            return repository.findByActionOrderByCreatedAtDesc(action);
        }
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/security")
    public List<AuditLog> getSecurityEvents() {
        List<String> securityActions = List.of(
            "LOGIN", "PASSWORD_SET", "PASSWORD_CHANGED", "USER_BLOCKED", "USER_UNBLOCKED",
            "USER_CREATED", "USER_DELETED", "PASSWORD_RESET", "MFA_ENABLED", "MFA_DISABLED"
        );
        return repository.findSecurityEvents(securityActions, "USER");
    }

    @GetMapping("/connected")
    public List<ConnectedUserDTO> getConnectedUsers() {
        return sessionTracker.getConnectedUsers();
    }
}

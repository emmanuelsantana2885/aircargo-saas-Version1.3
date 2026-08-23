package com.aircargo.authservice.service;

import com.aircargo.authservice.event.AuditEventStore;
import com.aircargo.authservice.event.AuditEventType;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application-facing audit facade (command side). Delegates every write to the
 * append-only {@link AuditEventStore}. Existing call sites keep working
 * unchanged; actions are normalized to {@link AuditEventType} names.
 */
@Service
public class AuditService {

    private final AuditEventStore eventStore;

    public AuditService(AuditEventStore eventStore) {
        this.eventStore = eventStore;
    }

    public void log(UUID userId, String email, String fullName, String action,
                    String entityType, String entityId, String details, String ipAddress) {
        eventStore.append(userId, email, fullName, action, entityType, entityId, details, ipAddress);
    }

    public void logLogin(UUID userId, String email, String fullName, String ipAddress) {
        log(userId, email, fullName, AuditEventType.LOGIN_SUCCEEDED, "USER", userId.toString(), null, ipAddress);
    }

    public void logLoginFailed(UUID userId, String attemptedEmail, int attemptCount,
                               UUID targetUserId, String reason, String ipAddress) {
        String payload = "{\"attemptCount\":" + attemptCount
                + ",\"reason\":\"" + reason + "\",\"targetUserId\":"
                + (targetUserId != null ? "\"" + targetUserId + "\"" : "null") + "}";
        log(userId, attemptedEmail, null, AuditEventType.LOGIN_FAILED, "USER",
                targetUserId != null ? targetUserId.toString() : attemptedEmail, payload, ipAddress);
    }

    public void logAccountLocked(UUID userId, String email, int attemptCount,
                                 java.time.OffsetDateTime lockedUntil, String ipAddress) {
        String payload = "{\"attemptCount\":" + attemptCount
                + ",\"lockedUntil\":\"" + lockedUntil + "\"}";
        log(userId, email, null, AuditEventType.ACCOUNT_LOCKED, "USER", userId.toString(), payload, ipAddress);
    }

    public void logUserCreate(UUID userId, String email, String fullName,
                              UUID targetUserId, String targetEmail, String ipAddress) {
        log(userId, email, fullName, AuditEventType.USER_CREATED, "USER", targetUserId.toString(),
                "{\"email\":\"" + targetEmail + "\"}", ipAddress);
    }

    public void logUserUpdate(UUID userId, String email, String fullName,
                              UUID targetUserId, String changes, String ipAddress) {
        log(userId, email, fullName, AuditEventType.USER_UPDATED, "USER", targetUserId.toString(),
                changes, ipAddress);
    }

    public void logUserDelete(UUID userId, String email, String fullName,
                              UUID targetUserId, String targetEmail, String ipAddress) {
        log(userId, email, fullName, AuditEventType.USER_DELETED, "USER", targetUserId.toString(),
                "{\"email\":\"" + targetEmail + "\"}", ipAddress);
    }

    public void logPasswordReset(UUID userId, String email, String fullName,
                                 UUID targetUserId, String targetEmail, String ipAddress) {
        log(userId, email, fullName, AuditEventType.PASSWORD_RESET, "USER", targetUserId.toString(),
                "{\"email\":\"" + targetEmail + "\"}", ipAddress);
    }
}

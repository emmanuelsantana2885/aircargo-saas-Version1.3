package com.aircargo.authservice.command;

import java.util.Map;

/**
 * Result of handling a {@link SetPasswordCommand} or a {@link ChangePasswordCommand}:
 * either a success payload or an error body for the controller to map to HTTP.
 */
public record PasswordOutcome(Status status, Map<String, Object> body) {

    public enum Status {
        SUCCESS,
        USER_NOT_FOUND,
        USER_GONE,
        INACTIVE,
        CURRENT_PASSWORD_INCORRECT,
        CURRENT_PASSWORD_REQUIRED,
        UNEXPECTED_CURRENT_PASSWORD,
        MFA_NOT_CONFIGURED,
        MFA_ACCOUNT_LOCKED,
        MFA_ENROLLMENT_REQUIRED,
        TOTP_INVALID
    }

    public static PasswordOutcome success(Map<String, Object> body) {
        return new PasswordOutcome(Status.SUCCESS, body);
    }

    public static PasswordOutcome failure(Status status, Map<String, Object> body) {
        return new PasswordOutcome(status, body);
    }
}

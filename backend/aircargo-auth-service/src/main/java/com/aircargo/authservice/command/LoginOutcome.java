package com.aircargo.authservice.command;

import com.aircargo.authservice.dto.LoginResponse;

import java.util.Map;

/**
 * Result of handling a {@link LoginCommand}. The controller maps each status
 * to its HTTP contract; the handler never touches HTTP types.
 */
public record LoginOutcome(Status status, LoginResponse body, Map<String, Object> errorBody) {

    public enum Status {
        SUCCESS,
        INVALID_CREDENTIALS,
        INACTIVE,
        LOCKED,
        BLOCKED,
        PASSWORD_REQUIRED,
        MFA_REQUIRED,
        MFA_INVALID,
        MFA_LOCKED
    }

    public static LoginOutcome success(LoginResponse response) {
        return new LoginOutcome(Status.SUCCESS, response, null);
    }

    public static LoginOutcome failure(Status status, Map<String, Object> errorBody) {
        return new LoginOutcome(status, null, errorBody);
    }
}

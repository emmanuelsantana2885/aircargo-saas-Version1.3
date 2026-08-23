package com.aircargo.authservice.event;

/**
 * Domain event types persisted in the append-only audit event store.
 * Naming convention: <AGGREGATE>_<WHAT_HAPPENED> in past tense.
 */
public final class AuditEventType {

    private AuditEventType() {
    }

    // Authentication aggregate
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String LOGOUT = "LOGOUT";

    // User aggregate
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String USER_BLOCKED = "USER_BLOCKED";
    public static final String USER_UNBLOCKED = "USER_UNBLOCKED";

    // Credentials aggregate
    public static final String PASSWORD_SET = "PASSWORD_SET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String TEMP_PASSWORD_GENERATED = "TEMP_PASSWORD_GENERATED";

    // MFA
    public static final String MFA_ENABLED = "MFA_ENABLED";
    public static final String MFA_DISABLED = "MFA_DISABLED";
    public static final String MFA_LOCKED = "MFA_LOCKED";
    public static final String MFA_UNLOCKED = "MFA_UNLOCKED";

    // Service tokens
    public static final String SERVICE_TOKEN_GENERATED = "SERVICE_TOKEN_GENERATED";

    /** Actions considered security-relevant for the /audit-logs/security query (includes legacy names). */
    public static final java.util.List<String> SECURITY_ACTIONS = java.util.List.of(
            LOGIN_SUCCEEDED, LOGIN_FAILED, ACCOUNT_LOCKED,
            PASSWORD_SET, PASSWORD_CHANGED, PASSWORD_RESET, TEMP_PASSWORD_GENERATED,
            USER_BLOCKED, USER_UNBLOCKED,
            USER_CREATED, USER_DELETED,
            MFA_ENABLED, MFA_DISABLED,
            // legacy names written before event sourcing (kept for historical queries)
            "LOGIN", "CREATE", "DELETE"
    );
}

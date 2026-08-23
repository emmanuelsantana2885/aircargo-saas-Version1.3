package com.aircargo.authservice.command;

/**
 * Command: change the authenticated user's own password. Requires MFA
 * verification per role rules; the current password is re-verified unless the
 * change was forced by an administrator (mustChangePassword).
 */
public record ChangePasswordCommand(java.util.UUID userId, String newPassword,
                                    String currentPassword, String totpCode, String ipAddress) {
}

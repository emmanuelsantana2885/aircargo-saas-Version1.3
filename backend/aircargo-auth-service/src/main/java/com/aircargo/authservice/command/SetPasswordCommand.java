package com.aircargo.authservice.command;

/**
 * Command: set (first time) or change the password for an email account.
 * The new password is validated by {@code @StrongPassword} on the request DTO
 * before this command is built.
 */
public record SetPasswordCommand(String email, String newPassword, String currentPassword, String ipAddress) {
}

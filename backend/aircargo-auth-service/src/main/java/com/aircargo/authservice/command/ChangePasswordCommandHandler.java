package com.aircargo.authservice.command;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.event.AuditEventType;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.service.AuditService;
import com.aircargo.authservice.service.MfaService;
import com.aircargo.common.auth.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Command handler for the authenticated change-password flow (write side).
 * Enforces MFA verification, re-verifies the current password against the
 * BCrypt hash (unless the change was forced), stores the new hash, resets the
 * failed-attempt counter and appends a PASSWORD_CHANGED event.
 */
@Service
public class ChangePasswordCommandHandler {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final MfaService mfaService;
    private final JwtUtil jwtUtil;

    public ChangePasswordCommandHandler(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                                        AuditService auditService, MfaService mfaService, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.mfaService = mfaService;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public PasswordOutcome handle(ChangePasswordCommand command) {
        AppUser user = userRepository.findById(command.userId()).orElse(null);
        if (user == null) {
            return PasswordOutcome.failure(PasswordOutcome.Status.USER_GONE, Map.of());
        }

        // MFA verification — mandatory for non-SuperUser; SuperUser verifies only if MFA is enabled
        boolean mfaEnabled = Boolean.TRUE.equals(user.getMfaEnabled());
        if (user.getRole() != UserRole.SUPER_USER) {
            if (!mfaEnabled || user.getMfaSecret() == null) {
                return PasswordOutcome.failure(PasswordOutcome.Status.MFA_NOT_CONFIGURED, Map.of(
                        "error", "Debes configurar autenticación de dos factores antes de cambiar tu contraseña"
                ));
            }
        }
        if (mfaEnabled) {
            if (Boolean.TRUE.equals(user.getMfaLocked())) {
                return PasswordOutcome.failure(PasswordOutcome.Status.MFA_ACCOUNT_LOCKED,
                        Map.of("error", "Cuenta bloqueada por intentos fallidos de MFA"));
            }
            if (!mfaService.verifyCode(user.getMfaSecret(), command.totpCode())) {
                return PasswordOutcome.failure(PasswordOutcome.Status.TOTP_INVALID,
                        Map.of("error", "Código TOTP inválido"));
            }
        }

        // If not forced change, validate current password
        if (!Boolean.TRUE.equals(user.getMustChangePassword())) {
            if (command.currentPassword() == null || command.currentPassword().isBlank()) {
                return PasswordOutcome.failure(PasswordOutcome.Status.CURRENT_PASSWORD_REQUIRED,
                        Map.of("error", "Se requiere la contraseña actual"));
            }
            if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(command.currentPassword(), user.getPasswordHash())) {
                return PasswordOutcome.failure(PasswordOutcome.Status.CURRENT_PASSWORD_INCORRECT,
                        Map.of("error", "Contraseña actual incorrecta"));
            }
        }

        user.setPasswordHash(passwordEncoder.encode(command.newPassword()));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setTokensValidFrom(java.time.OffsetDateTime.now());  // revoca tokens previos
        userRepository.save(user);

        auditService.log(user.getId(), user.getEmail(), user.getFullName(), AuditEventType.PASSWORD_CHANGED,
                "USER", user.getId().toString(), null, command.ipAddress());

        String token = jwtUtil.generateToken(
                user.getId().toString(),
                user.getRole().name(),
                user.getAirline() != null && user.getAirline().getId() != null
                        ? user.getAirline().getId().toString() : "",
                user.getEmail(),
                user.getFullName()
        );

        return PasswordOutcome.success(Map.of(
                "message", "Contraseña cambiada correctamente",
                "token", token
        ));
    }
}

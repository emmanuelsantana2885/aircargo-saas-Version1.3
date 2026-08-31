package com.aircargo.authservice.command;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.event.AuditEventType;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.service.AuditService;
import com.aircargo.common.auth.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Command handler for first-time password set / password change by email
 * (write side). Verifies the current password against the stored BCrypt hash,
 * stores the new hash and appends a PASSWORD_SET event.
 */
@Service
public class SetPasswordCommandHandler {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final JwtUtil jwtUtil;
    private final boolean mfaMandatory;

    public SetPasswordCommandHandler(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                                     AuditService auditService, JwtUtil jwtUtil,
                                     @org.springframework.beans.factory.annotation.Value("${app.mfa.mandatory:true}") boolean mfaMandatory) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.jwtUtil = jwtUtil;
        this.mfaMandatory = mfaMandatory;
    }

    @Transactional
    public PasswordOutcome handle(SetPasswordCommand command) {
        AppUser user = userRepository.findByEmail(command.email()).orElse(null);
        if (user == null) {
            return PasswordOutcome.failure(PasswordOutcome.Status.USER_NOT_FOUND,
                    Map.of("error", "Usuario no encontrado"));
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return PasswordOutcome.failure(PasswordOutcome.Status.INACTIVE,
                    Map.of("error", "Usuario inactivo"));
        }

        String currentHash = user.getPasswordHash();
        if (currentHash != null && !currentHash.isBlank()) {
            if (command.currentPassword() == null || !passwordEncoder.matches(command.currentPassword(), currentHash)) {
                return PasswordOutcome.failure(PasswordOutcome.Status.CURRENT_PASSWORD_INCORRECT,
                        Map.of("error", "Contraseña actual incorrecta"));
            }
        } else {
            if (command.currentPassword() != null && !command.currentPassword().isBlank()) {
                return PasswordOutcome.failure(PasswordOutcome.Status.UNEXPECTED_CURRENT_PASSWORD,
                        Map.of("error", "Este usuario no tiene contraseña previa. No envíe contraseña actual."));
            }
        }

        // BCrypt hash — the clear password is never persisted or logged
        user.setPasswordHash(passwordEncoder.encode(command.newPassword()));
        user.setTokensValidFrom(java.time.OffsetDateTime.now());  // revoca tokens previos
        userRepository.save(user);

        auditService.log(user.getId(), user.getEmail(), user.getFullName(), AuditEventType.PASSWORD_SET,
                "USER", user.getId().toString(), null, command.ipAddress());

        // MFA obligatorio: sin MFA configurado NO se emite sesión — flujo de enrolamiento
        if (mfaMandatory && !Boolean.TRUE.equals(user.getMfaEnabled())) {
            String enrollToken = jwtUtil.generateEnrollToken(
                    user.getId().toString(), user.getRole().name(), user.getEmail(), user.getFullName());
            return PasswordOutcome.failure(PasswordOutcome.Status.MFA_ENROLLMENT_REQUIRED, Map.of(
                    "mfaEnrollmentRequired", true,
                    "enrollToken", enrollToken,
                    "email", user.getEmail(),
                    "message", "Configura la autenticación de dos factores (MFA) para continuar"
            ));
        }

        String token = jwtUtil.generateToken(
                user.getId().toString(),
                user.getRole().name(),
                user.getAirline() != null && user.getAirline().getId() != null
                        ? user.getAirline().getId().toString() : "",
                user.getEmail(),
                user.getFullName()
        );
        return PasswordOutcome.success(Map.of(
                "message", "Contraseña establecida correctamente",
                "token", token
        ));
    }
}

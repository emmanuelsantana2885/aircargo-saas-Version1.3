package com.aircargo.authservice.command;

import com.aircargo.authservice.dto.LoginResponse;
import com.aircargo.authservice.dto.SiteDTO;
import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.repository.SiteRepository;
import com.aircargo.authservice.service.ActiveSessionTracker;
import com.aircargo.authservice.service.AuditService;
import com.aircargo.authservice.service.MfaService;
import com.aircargo.common.auth.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command handler for authentication (write side of the CQRS split).
 * Owns all login business rules: account state checks, the failed-attempt
 * limit (5) with temporary lockout, password verification against the BCrypt
 * hash, MFA verification and JWT issuance. Every relevant fact is appended to
 * the audit event store.
 */
@Service
public class LoginCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginCommandHandler.class);

    /** Maximum consecutive failed login attempts before temporary lockout. */
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    /** How long an account stays locked after reaching the attempt limit. */
    public static final long LOCKOUT_MINUTES = 30;

    /** Generic, deliberately vague message for any bad-credential outcome. */
    public static final String MSG_INVALID_CREDENTIALS = "Email y/o contraseña incorrectos";

    private final AppUserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ActiveSessionTracker sessionTracker;
    private final SiteRepository siteRepository;
    private final MfaService mfaService;

    public LoginCommandHandler(AppUserRepository userRepository, JwtUtil jwtUtil,
                               PasswordEncoder passwordEncoder, AuditService auditService,
                               ActiveSessionTracker sessionTracker, SiteRepository siteRepository,
                               MfaService mfaService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.sessionTracker = sessionTracker;
        this.siteRepository = siteRepository;
        this.mfaService = mfaService;
    }

    @Transactional
    public LoginOutcome handle(LoginCommand command) {
        AppUser user = userRepository.findByEmail(command.email()).orElse(null);
        if (user == null) {
            // Same generic message as wrong-password: never reveal which emails exist.
            auditService.logLoginFailed(null, command.email(), 0, null, "UNKNOWN_USER", command.ipAddress());
            return LoginOutcome.failure(LoginOutcome.Status.INVALID_CREDENTIALS,
                    Map.of("error", MSG_INVALID_CREDENTIALS));
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return LoginOutcome.failure(LoginOutcome.Status.INACTIVE,
                    Map.of("error", "Usuario inactivo"));
        }

        // Temporary lockout after too many failed attempts
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            long minutesRemaining = Duration.between(OffsetDateTime.now(), user.getLockedUntil()).toMinutes();
            return LoginOutcome.failure(LoginOutcome.Status.LOCKED,
                    Map.of("error", "Cuenta bloqueada. Intente de nuevo en " + minutesRemaining + " minutos."));
        }

        // Manual admin block
        if (Boolean.TRUE.equals(user.getBlocked())) {
            return LoginOutcome.failure(LoginOutcome.Status.BLOCKED,
                    Map.of("error", "Account blocked. Contact your administrator."));
        }

        String passwordHash = user.getPasswordHash();
        boolean hasPasswordSet = passwordHash != null && !passwordHash.isBlank();

        if (hasPasswordSet) {
            if (command.password() == null || command.password().isBlank()) {
                return LoginOutcome.failure(LoginOutcome.Status.PASSWORD_REQUIRED,
                        Map.of("error", "Contraseña requerida"));
            }
            if (!passwordEncoder.matches(command.password(), passwordHash)) {
                return registerFailedAttempt(user, command);
            }
            // Success resets the failed-attempt counter
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        // MFA check — all roles with MFA enabled must verify (no role bypass)
        if (mfaService.isMfaRequired(user)) {
            if (Boolean.TRUE.equals(user.getMfaLocked())) {
                return LoginOutcome.failure(LoginOutcome.Status.MFA_LOCKED,
                        Map.of("error", "Cuenta bloqueada por intentos fallidos de MFA. Contacte al administrador."));
            }
            if (command.totpCode() == null || command.totpCode().isBlank()) {
                return LoginOutcome.failure(LoginOutcome.Status.MFA_REQUIRED,
                        Map.of(
                                "mfaRequired", true,
                                "message", "Se requiere código de autenticación de dos factores"
                        ));
            }
            if (!mfaService.verifyCode(user.getMfaSecret(), command.totpCode())) {
                return LoginOutcome.failure(LoginOutcome.Status.MFA_INVALID,
                        Map.of("error", "Código de autenticación inválido"));
            }
        }

        user.setLastLogin(OffsetDateTime.now());
        userRepository.save(user);

        String airlineIdStr = user.getAirline() != null && user.getAirline().getId() != null
                ? user.getAirline().getId().toString() : "";

        String token = jwtUtil.generateToken(
                user.getId().toString(),
                user.getRole().name(),
                airlineIdStr,
                user.getEmail(),
                user.getFullName()
        );
        String refreshToken = jwtUtil.generateRefreshToken(user.getId().toString());

        auditService.logLogin(user.getId(), user.getEmail(), user.getFullName(), command.ipAddress());

        sessionTracker.recordHeartbeat(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(), user.getLastLogin());

        List<SiteDTO> userSites = resolveSites(user);

        return LoginOutcome.success(new LoginResponse(
                token,
                refreshToken,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getAirline() != null ? user.getAirline().getId() : null,
                hasPasswordSet,
                userSites,
                Boolean.TRUE.equals(user.getMustChangePassword()),
                Boolean.TRUE.equals(user.getMfaEnabled())
        ));
    }

    private LoginOutcome registerFailedAttempt(AppUser user, LoginCommand command) {
        int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
        user.setFailedLoginAttempts(attempts);

        auditService.logLoginFailed(user.getId(), user.getEmail(), attempts, user.getId(),
                "INVALID_PASSWORD", command.ipAddress());

        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            OffsetDateTime lockedUntil = OffsetDateTime.now().plusMinutes(LOCKOUT_MINUTES);
            user.setLockedUntil(lockedUntil);
            auditService.logAccountLocked(user.getId(), user.getEmail(), attempts, lockedUntil,
                    command.ipAddress());
            log.warn("Account locked for {} after {} failed attempts", user.getEmail(), attempts);
        }
        userRepository.save(user);

        return LoginOutcome.failure(LoginOutcome.Status.INVALID_CREDENTIALS,
                Map.of("error", MSG_INVALID_CREDENTIALS));
    }

    private List<SiteDTO> resolveSites(AppUser user) {
        if (user.getRole() == UserRole.SUPER_USER && user.getSites().isEmpty()) {
            return siteRepository.findByIsActiveTrue().stream()
                    .map(SiteDTO::fromEntity)
                    .collect(Collectors.toList());
        }
        return user.getSites().stream()
                .map(SiteDTO::fromEntity)
                .collect(Collectors.toList());
    }
}

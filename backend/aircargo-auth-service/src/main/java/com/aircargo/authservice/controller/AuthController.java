package com.aircargo.authservice.controller;

import com.aircargo.common.auth.JwtUtil;
import com.aircargo.common.auth.UserPrincipal;
import com.aircargo.authservice.dto.ChangePasswordRequest;
import com.aircargo.authservice.dto.LoginRequest;
import com.aircargo.authservice.dto.LoginResponse;
import com.aircargo.authservice.dto.SetPasswordRequest;
import com.aircargo.authservice.dto.SiteDTO;
import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.repository.SiteRepository;
import com.aircargo.authservice.service.ActiveSessionTracker;
import com.aircargo.authservice.service.AuditService;
import com.aircargo.authservice.service.MfaService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 30;

    private final AppUserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ActiveSessionTracker sessionTracker;
    private final SiteRepository siteRepository;
    private final MfaService mfaService;
    private final CacheManager cacheManager;

    public AuthController(AppUserRepository userRepository, JwtUtil jwtUtil,
                          AuditService auditService, ActiveSessionTracker sessionTracker,
                          SiteRepository siteRepository, MfaService mfaService,
                          CacheManager cacheManager) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.auditService = auditService;
        this.sessionTracker = sessionTracker;
        this.siteRepository = siteRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.mfaService = mfaService;
        this.cacheManager = cacheManager;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest servletRequest) {
        AppUser user = userRepository.findByEmail(request.email())
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales inválidas"));
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Usuario inactivo"));
        }

        // Account lockout check
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            long minutesRemaining = java.time.Duration.between(OffsetDateTime.now(), user.getLockedUntil()).toMinutes();
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cuenta bloqueada. Intente de nuevo en " + minutesRemaining + " minutos."));
        }

        // Blocked check
        if (Boolean.TRUE.equals(user.getBlocked())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Account blocked. Contact your administrator."));
        }

        String passwordHash = user.getPasswordHash();
        boolean hasPasswordSet = passwordHash != null && !passwordHash.isBlank();

        if (hasPasswordSet) {
            if (request.password() == null || request.password().isBlank()) {
                return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                        .body(Map.of("error", "Contraseña requerida"));
            }
            if (!passwordEncoder.matches(request.password(), passwordHash)) {
                // Increment failed attempts
                int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
                user.setFailedLoginAttempts(attempts);
                if (attempts >= MAX_LOGIN_ATTEMPTS) {
                    user.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                    log.warn("Account locked for {} after {} failed attempts", user.getEmail(), attempts);
                }
                userRepository.save(user);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Credenciales inválidas"));
            }
            // Reset failed attempts on success
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        // MFA check — all roles with MFA enabled must verify (no role bypass)
        if (mfaService.isMfaRequired(user)) {
            if (Boolean.TRUE.equals(user.getMfaLocked())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Cuenta bloqueada por intentos fallidos de MFA. Contacte al administrador."));
            }
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                        .body(Map.of(
                                "mfaRequired", true,
                                "message", "Se requiere código de autenticación de dos factores"
                        ));
            }
            if (!mfaService.verifyCode(user.getMfaSecret(), request.totpCode())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Código de autenticación inválido"));
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

        auditService.logLogin(user.getId(), user.getEmail(), user.getFullName(), servletRequest.getRemoteAddr());

        sessionTracker.recordHeartbeat(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(), user.getLastLogin());

        List<SiteDTO> userSites;
        if (user.getRole() == UserRole.SUPER_USER && user.getSites().isEmpty()) {
            userSites = siteRepository.findByIsActiveTrue().stream()
                    .map(SiteDTO::fromEntity)
                    .collect(Collectors.toList());
        } else {
            userSites = user.getSites().stream()
                    .map(SiteDTO::fromEntity)
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(new LoginResponse(
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

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token required"));
        }

        try {
            if (!jwtUtil.isValid(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired refresh token"));
            }

            Claims claims = jwtUtil.parseToken(refreshToken);
            String tokenType = claims.get("tokenType", String.class);
            if (!"refresh".equals(tokenType)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid token type"));
            }

            String userId = claims.getSubject();
            AppUser user = userRepository.findById(java.util.UUID.fromString(userId)).orElse(null);
            if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not found or inactive"));
            }

            String airlineIdStr = user.getAirline() != null && user.getAirline().getId() != null
                    ? user.getAirline().getId().toString() : "";

            String newAccessToken = jwtUtil.generateToken(
                    user.getId().toString(),
                    user.getRole().name(),
                    airlineIdStr,
                    user.getEmail(),
                    user.getFullName()
            );
            String newRefreshToken = jwtUtil.generateRefreshToken(user.getId().toString());

            return ResponseEntity.ok(Map.of(
                    "token", newAccessToken,
                    "refreshToken", newRefreshToken
            ));

        } catch (Exception e) {
            log.warn("Refresh token validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid refresh token"));
        }
    }

    @PostMapping("/set-password")
    public ResponseEntity<?> setPassword(@Valid @RequestBody SetPasswordRequest request,
                                          HttpServletRequest servletRequest) {
        AppUser user = userRepository.findByEmail(request.email())
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Usuario no encontrado"));
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Usuario inactivo"));
        }

        String currentHash = user.getPasswordHash();
        if (currentHash != null && !currentHash.isBlank()) {
            if (request.currentPassword() == null || !passwordEncoder.matches(request.currentPassword(), currentHash)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Contraseña actual incorrecta"));
            }
        } else {
            if (request.currentPassword() != null && !request.currentPassword().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Este usuario no tiene contraseña previa. No envíe contraseña actual."));
            }
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        auditService.log(user.getId(), user.getEmail(), user.getFullName(), "PASSWORD_SET",
                "USER", user.getId().toString(), null, servletRequest.getRemoteAddr());

        String airlineIdStr = user.getAirline() != null && user.getAirline().getId() != null
                ? user.getAirline().getId().toString() : "";

        String token = jwtUtil.generateToken(
                user.getId().toString(),
                user.getRole().name(),
                airlineIdStr,
                user.getEmail(),
                user.getFullName()
        );
        return ResponseEntity.ok(Map.of(
                "message", "Contraseña establecida correctamente",
                "token", token
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                             @AuthenticationPrincipal UserPrincipal principal,
                                             HttpServletRequest servletRequest) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = userRepository.findById(principal.getUserIdAsUuid()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // MFA verification — mandatory for non-SuperUser; SuperUser verifies only if MFA is enabled
        boolean mfaEnabled = Boolean.TRUE.equals(user.getMfaEnabled());
        if (user.getRole() != UserRole.SUPER_USER) {
            if (!mfaEnabled || user.getMfaSecret() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Debes configurar autenticación de dos factores antes de cambiar tu contraseña"
                ));
            }
        }
        if (mfaEnabled) {
            if (Boolean.TRUE.equals(user.getMfaLocked())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Cuenta bloqueada por intentos fallidos de MFA"));
            }
            if (!mfaService.verifyCode(user.getMfaSecret(), request.totpCode())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Código TOTP inválido"));
            }
        }

        // If not forced change, validate current password
        if (!Boolean.TRUE.equals(user.getMustChangePassword())) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Se requiere la contraseña actual"
                ));
            }
            if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Contraseña actual incorrecta"));
            }
        }

        // Save new password
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        auditService.log(user.getId(), user.getEmail(), user.getFullName(), "PASSWORD_CHANGED",
                "USER", user.getId().toString(), null, servletRequest.getRemoteAddr());

        // Generate new token
        String airlineIdStr = user.getAirline() != null && user.getAirline().getId() != null
                ? user.getAirline().getId().toString() : "";

        String token = jwtUtil.generateToken(
                user.getId().toString(),
                user.getRole().name(),
                airlineIdStr,
                user.getEmail(),
                user.getFullName()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Contraseña cambiada correctamente",
                "token", token
        ));
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<?> setupMfa(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AppUser user = userRepository.findById(principal.getUserIdAsUuid()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
        }
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA ya está habilitado para este usuario"));
        }
        String secret = mfaService.generateSecret();
        String otpAuthUrl = mfaService.getOtpAuthUrl(user.getEmail(), secret);
        return ResponseEntity.ok(Map.of(
                "secret", secret,
                "otpAuthUrl", otpAuthUrl
        ));
    }

    @PostMapping("/mfa/enable")
    public ResponseEntity<?> enableMfa(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody Map<String, String> body,
                                       HttpServletRequest servletRequest) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AppUser user = userRepository.findById(principal.getUserIdAsUuid()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
        }
        String secret = body.get("secret");
        String totpCode = body.get("totpCode");
        if (secret == null || totpCode == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "secret y totpCode son requeridos"));
        }
        if (!mfaService.verifyCode(secret, totpCode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Código TOTP inválido"));
        }
        mfaService.enableMfa(user.getId(), secret);
        auditService.log(user.getId(), user.getEmail(), user.getFullName(),
                "MFA_ENABLED", "USER", user.getId().toString(), null, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "MFA habilitado correctamente"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestBody(required = false) Map<String, String> body,
                                    HttpServletRequest servletRequest) {
        String authHeader = servletRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtUtil.revokeToken(authHeader.substring(7));
        }
        String refreshToken = body != null ? body.get("refreshToken") : null;
        if (refreshToken != null && !refreshToken.isBlank()) {
            jwtUtil.revokeToken(refreshToken);
        }
        if (principal != null) {
            sessionTracker.removeSession(principal.getUserIdAsUuid());
            auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                    "LOGOUT", "USER", principal.getUserIdAsUuid().toString(), null,
                    servletRequest.getRemoteAddr());
        }
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente"));
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AppUser user = userRepository.findById(principal.getUserIdAsUuid()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean hasPasswordSet = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        List<SiteDTO> userSites;
        if (user.getRole() == UserRole.SUPER_USER && user.getSites().isEmpty()) {
            userSites = siteRepository.findByIsActiveTrue().stream()
                    .map(SiteDTO::fromEntity)
                    .collect(Collectors.toList());
        } else {
            userSites = user.getSites().stream()
                    .map(SiteDTO::fromEntity)
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(new LoginResponse(
                null, null, user.getId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.getAirline() != null ? user.getAirline().getId() : null,
                hasPasswordSet, userSites,
                Boolean.TRUE.equals(user.getMustChangePassword()),
                Boolean.TRUE.equals(user.getMfaEnabled())
        ));
    }

    @GetMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal != null) {
            sessionTracker.recordHeartbeat(
                    principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                    principal.role(), OffsetDateTime.now());
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/service-token")
    public ResponseEntity<?> generateServiceToken(@RequestBody Map<String, String> body,
                                                  @AuthenticationPrincipal UserPrincipal principal,
                                                  HttpServletRequest servletRequest) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"SUPER_USER".equals(principal.role()) && !"ADMIN".equals(principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Solo ADMIN o SUPER_USER pueden generar tokens de servicio"));
        }

        String targetEmail = body.get("email");
        if (targetEmail == null || targetEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email es requerido"));
        }

        AppUser targetUser = userRepository.findByEmail(targetEmail).orElse(null);
        if (targetUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Usuario no encontrado: " + targetEmail));
        }
        if (!Boolean.TRUE.equals(targetUser.getIsActive())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Usuario inactivo"));
        }

        String airlineIdStr = targetUser.getAirline() != null && targetUser.getAirline().getId() != null
                ? targetUser.getAirline().getId().toString() : "";

        String serviceToken = jwtUtil.generateServiceToken(
                targetUser.getId().toString(),
                targetUser.getRole().name(),
                airlineIdStr,
                targetUser.getEmail(),
                targetUser.getFullName()
        );

        auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                "SERVICE_TOKEN_GENERATED", "USER", targetUser.getId().toString(),
                "Token de servicio generado para " + targetEmail, servletRequest.getRemoteAddr());

        return ResponseEntity.ok(Map.of(
                "token", serviceToken,
                "email", targetUser.getEmail(),
                "role", targetUser.getRole().name(),
                "expiresIn", "365 days",
                "usage", "Authorization: Bearer <token>"
        ));
    }

    @PostMapping("/block/{userId}")
    public ResponseEntity<?> blockUser(@PathVariable UUID userId,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest servletRequest) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        AppUser caller = userRepository.findById(principal.getUserIdAsUuid()).orElse(null);
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (caller.getRole() != UserRole.SUPER_USER && caller.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Insufficient permissions"));
        }
        if (userId.equals(principal.getUserIdAsUuid())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot block yourself"));
        }
        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        user.setBlocked(true);
        userRepository.save(user);
        evictUsersCache();
        auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                "USER_BLOCKED", "USER", userId.toString(),
                "Blocked user " + user.getEmail(), servletRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "User blocked", "blocked", true));
    }

    @PostMapping("/unblock/{userId}")
    public ResponseEntity<?> unblockUser(@PathVariable UUID userId,
                                          @AuthenticationPrincipal UserPrincipal principal,
                                          HttpServletRequest servletRequest) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        AppUser caller = userRepository.findById(principal.getUserIdAsUuid()).orElse(null);
        if (caller == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (caller.getRole() != UserRole.SUPER_USER && caller.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Insufficient permissions"));
        }
        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        user.setBlocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        evictUsersCache();
        auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                "USER_UNBLOCKED", "USER", userId.toString(),
                "Unblocked user " + user.getEmail(), servletRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "User unblocked", "blocked", false));
    }

    private void evictUsersCache() {
        Cache cache = cacheManager.getCache("users");
        if (cache != null) {
            cache.clear();
        }
    }
}

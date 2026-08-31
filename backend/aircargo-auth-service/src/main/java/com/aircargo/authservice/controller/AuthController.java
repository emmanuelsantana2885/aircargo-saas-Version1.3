package com.aircargo.authservice.controller;

import com.aircargo.authservice.command.ChangePasswordCommand;
import com.aircargo.authservice.command.LoginCommand;
import com.aircargo.authservice.command.LoginCommandHandler;
import com.aircargo.authservice.command.LoginOutcome;
import com.aircargo.authservice.command.PasswordOutcome;
import com.aircargo.authservice.command.SetPasswordCommand;
import com.aircargo.authservice.command.SetPasswordCommandHandler;
import com.aircargo.common.auth.CookieAuthSupport;
import com.aircargo.common.auth.JwtUtil;
import com.aircargo.common.auth.UserPrincipal;
import com.aircargo.authservice.dto.ChangePasswordRequest;
import com.aircargo.authservice.dto.LoginRequest;
import com.aircargo.authservice.dto.LoginResponse;
import com.aircargo.authservice.dto.SetPasswordRequest;
import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.repository.SiteRepository;
import com.aircargo.authservice.service.ActiveSessionTracker;
import com.aircargo.authservice.service.AuditService;
import com.aircargo.authservice.service.MfaPolicyService;
import com.aircargo.authservice.service.MfaPolicyService.MfaEligibility;
import com.aircargo.authservice.service.MfaService;
import com.aircargo.authservice.service.PasswordResetService;
import com.aircargo.authservice.service.TokenRevocationService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Thin HTTP adapter (CQRS): maps requests to command handlers / queries and
 * translates outcomes to HTTP responses. No business logic lives here.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final LoginCommandHandler loginHandler;
    private final SetPasswordCommandHandler setPasswordHandler;
    private final com.aircargo.authservice.command.ChangePasswordCommandHandler changePasswordHandler;
    private final AppUserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;
    private final ActiveSessionTracker sessionTracker;
    private final SiteRepository siteRepository;
    private final MfaService mfaService;
    private final MfaPolicyService mfaPolicyService;
    private final CacheManager cacheManager;
    private final PasswordResetService passwordResetService;
    private final TokenRevocationService tokenRevocationService;

    @org.springframework.beans.factory.annotation.Value("${app.jwt.cookie-secure:false}")
    private boolean cookieSecure;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${app.mfa.mandatory:true}")
    private boolean mfaMandatory;

    public AuthController(LoginCommandHandler loginHandler,
                          SetPasswordCommandHandler setPasswordHandler,
                          com.aircargo.authservice.command.ChangePasswordCommandHandler changePasswordHandler,
                          AppUserRepository userRepository, JwtUtil jwtUtil,
                          AuditService auditService, ActiveSessionTracker sessionTracker,
                          SiteRepository siteRepository, MfaService mfaService,
                          MfaPolicyService mfaPolicyService,
                          CacheManager cacheManager,
                          PasswordResetService passwordResetService,
                          TokenRevocationService tokenRevocationService,
                          org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.loginHandler = loginHandler;
        this.setPasswordHandler = setPasswordHandler;
        this.changePasswordHandler = changePasswordHandler;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.auditService = auditService;
        this.sessionTracker = sessionTracker;
        this.siteRepository = siteRepository;
        this.mfaService = mfaService;
        this.mfaPolicyService = mfaPolicyService;
        this.cacheManager = cacheManager;
        this.passwordResetService = passwordResetService;
        this.tokenRevocationService = tokenRevocationService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest servletRequest) {
        LoginOutcome outcome = loginHandler.handle(
                new LoginCommand(request.email(), request.password(), request.totpCode(),
                        servletRequest.getRemoteAddr()));
        return switch (outcome.status()) {
            case SUCCESS -> withCookies(outcome.body(), 200);
            case INVALID_CREDENTIALS -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(outcome.errorBody());
            case INACTIVE, LOCKED, BLOCKED, MFA_LOCKED -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(outcome.errorBody());
            case PASSWORD_REQUIRED, MFA_REQUIRED, MFA_ENROLLMENT_REQUIRED ->
                    ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(outcome.errorBody());
            case MFA_INVALID -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(outcome.errorBody());
        };
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody(required = false) Map<String, String> body,
                                     HttpServletRequest servletRequest) {
        // El refresh token llega por cookie httpOnly (o body por compatibilidad)
        String refreshToken = CookieAuthSupport.extractToken(servletRequest, CookieAuthSupport.REFRESH_COOKIE);
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = body != null ? body.get("refreshToken") : null;
        }
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
            AppUser user = userRepository.findById(UUID.fromString(userId)).orElse(null);
            if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not found or inactive"));
            }
            if (Boolean.TRUE.equals(user.getBlocked())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User blocked"));
            }
            // Revocación central: refresh tokens emitidos antes de tokens_valid_from mueren aquí
            java.time.OffsetDateTime iat = jwtUtil.getIssuedAt(refreshToken);
            if (iat != null && tokenRevocationService.isStale(user.getId(), iat)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Session revoked"));
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

            return withCookies(Map.of(
                    "token", newAccessToken,
                    "refreshToken", newRefreshToken
            ), 200);

        } catch (Exception e) {
            log.warn("Refresh token validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid refresh token"));
        }
    }

    /**
     * Valida un token de enlace de restablecimiento (un solo uso, 15 min).
     * Público: el frontend lo consulta antes de mostrar el formulario.
     */
    @PostMapping("/reset-password/validate")
    public ResponseEntity<?> validateResetToken(@jakarta.validation.Valid @RequestBody ResetTokenValidateRequest req) {
        if (passwordResetService.validate(req.token()).isPresent()) {
            return ResponseEntity.ok(java.util.Map.of("valid", true));
        }
        return ResponseEntity.badRequest().body(java.util.Map.of("error", "Enlace inválido o expirado"));
    }

    /**
     * Establece contraseña mediante token de enlace de un solo uso.
     * Reemplaza el flujo de contraseñas temporales compartidas: nadie
     * conoce la contraseña excepto el usuario que la define aquí.
     */
    @PostMapping("/set-password-token")
    public ResponseEntity<?> setPasswordByToken(@jakarta.validation.Valid @RequestBody SetPasswordByTokenRequest req,
                                                HttpServletRequest request) {
        var tokenOpt = passwordResetService.validate(req.token());
        if (tokenOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Enlace inválido o expirado"));
        }
        var token = tokenOpt.get();
        AppUser user = userRepository.findById(token.getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("error", "Usuario no encontrado"));
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", "Usuario inactivo"));
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setTokensValidFrom(java.time.OffsetDateTime.now());  // revoca tokens previos
        userRepository.save(user);
        tokenRevocationService.evict(user.getId());
        passwordResetService.markUsed(token);

        auditService.log(user.getId(), user.getEmail(), user.getFullName(),
                com.aircargo.authservice.event.AuditEventType.PASSWORD_SET, "USER",
                user.getId().toString(), null, request.getRemoteAddr());

        // MFA obligatorio: si la política exige MFA NO se emite sesión — flujo de
        // (re)enrolamiento (también para MFA caducado por reinicio o antigüedad).
        if (mfaMandatory) {
            MfaEligibility eligibility = mfaPolicyService.evaluate(user);
            if (eligibility != MfaEligibility.OK) {
                String enrollToken = jwtUtil.generateEnrollToken(
                        user.getId().toString(), user.getRole().name(), user.getEmail(), user.getFullName());
                String reason = switch (eligibility) {
                    case RESET_REQUIRED -> "reset";
                    case EXPIRED -> "expired";
                    default -> "required";
                };
                String message = switch (eligibility) {
                    case RESET_REQUIRED -> "Por seguridad, la autenticación de dos factores fue reiniciada tras una actualización del sistema. Debe configurarla nuevamente.";
                    case EXPIRED -> "Por seguridad, su configuración de dos factores caducó. Debe configurarla nuevamente para continuar.";
                    default -> "Configura la autenticación de dos factores (MFA) para continuar";
                };
                return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(java.util.Map.of(
                        "mfaEnrollmentRequired", true,
                        "enrollToken", enrollToken,
                        "email", user.getEmail(),
                        "mfaReason", reason,
                        "message", message
                ));
            }
        }

        String jwt = jwtUtil.generateToken(
                user.getId().toString(),
                user.getRole().name(),
                user.getAirline() != null && user.getAirline().getId() != null
                        ? user.getAirline().getId().toString() : "",
                user.getEmail(),
                user.getFullName()
        );
        return withCookies(java.util.Map.of(
                "message", "Contraseña establecida correctamente",
                "token", jwt
        ), 200);
    }

    public record ResetTokenValidateRequest(@jakarta.validation.constraints.NotBlank String token) {}

    public record SetPasswordByTokenRequest(
            @jakarta.validation.constraints.NotBlank String token,
            @jakarta.validation.constraints.NotBlank @com.aircargo.common.validation.StrongPassword String newPassword) {}


    /**
     * PASO 1 del enrolamiento forzado de MFA (pú个blico, sin sesión activa):
     * valida el enroll token y devuelve el secreto base32 + URL otpauth para el QR.
     * El enrollToken viaja en el BODY, no como Authorization, y su tokenType es
     * exclusivamente "enroll" (los filtros rechazan ese tipo como acceso general).
     */
    @PostMapping("/mfa/enroll/setup")
    public ResponseEntity<?> enrollMfaSetup(@RequestBody Map<String, String> body) {
        String enrollToken = body.get("enrollToken");
        boolean validEnroll = isValidEnrollToken(enrollToken);
        if (!validEnroll) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Enlace de enrolamiento inválido o expirado. Inicie sesión nuevamente."));
        }
        AppUser user = userRepository.findById(UUID.fromString(parseSubject(enrollToken))).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Usuario no encontrado o inactivo"));
        }
        // Se permite re-enrolar aunque el usuario ya tenga MFA si la política lo exige
        // (reinicio/actualización de la app o antigüedad superada). Un MFA vigente NO
        // puede re-enrolarse por esta vía — el enrollToken se emite solo cuando la
        // política exige re-configuración, nunca en un login normal.
        if (Boolean.TRUE.equals(user.getMfaEnabled()) && !mfaPolicyService.requiresReenrollment(user)) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA ya está habilitado y vigente para este usuario"));
        }
        String secret = mfaService.generateSecret();
        String otpAuthUrl = mfaService.getOtpAuthUrl(user.getEmail(), secret);
        return ResponseEntity.ok(Map.of(
                "secret", secret,
                "otpAuthUrl", otpAuthUrl,
                "email", user.getEmail()
        ));
    }

    /**
     * PASO 2 del enrolamiento forzado (público): valida el enroll token, verifica
     * el código TOTP contra el secreto, habilita MFA y revoca el enroll token
     * (un solo uso). Devuelve un enrollSuccess para que el frontend reinicie login.
     */
    @PostMapping("/mfa/enroll/enable")
    public ResponseEntity<?> enrollMfaEnable(@RequestBody Map<String, String> body,
                                             HttpServletRequest servletRequest) {
        String enrollToken = body.get("enrollToken");
        String secret = body.get("secret");
        String totpCode = body.get("totpCode");
        boolean validEnroll = isValidEnrollToken(enrollToken);
        if (!validEnroll) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Enlace de enrolamiento inválido o expirado. Inicie sesión nuevamente."));
        }
        if (secret == null || secret.isBlank() || totpCode == null || totpCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "secret y totpCode son requeridos"));
        }
        AppUser user = userRepository.findById(UUID.fromString(parseSubject(enrollToken))).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Usuario no encontrado o inactivo"));
        }
        // Re-enrolamiento permitido también con MFA previo (solo cuando la política lo exige).
        if (Boolean.TRUE.equals(user.getMfaEnabled()) && !mfaPolicyService.requiresReenrollment(user)) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA ya está habilitado y vigente para este usuario"));
        }
        if (!mfaService.verifyCode(secret, totpCode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Código TOTP inválido"));
        }
        mfaService.enableMfa(user.getId(), secret);
        jwtUtil.revokeToken(enrollToken); // un solo uso
        auditService.log(user.getId(), user.getEmail(), user.getFullName(),
                "MFA_ENROLLED", "USER", user.getId().toString(), null, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of(
                "message", "MFA configurado correctamente",
                "enrollSuccess", true,
                "email", user.getEmail()
        ));
    }

    private boolean isValidEnrollToken(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            if (jwtUtil.isRevoked(token) || !jwtUtil.isValid(token)) return false;
            Claims claims = jwtUtil.parseToken(token);
            return "enroll".equals(claims.get("tokenType", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    private String parseSubject(String token) {
        try {
            return jwtUtil.parseToken(token).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<?> withCookies(Object body, int status) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        String access = null;
        String refresh = null;
        if (body instanceof java.util.Map<?, ?> map) {
            Object a = map.get("token");
            Object r = map.get("refreshToken");
            if (a instanceof String s) access = s;
            if (r instanceof String s) refresh = s;
        } else if (body instanceof LoginResponse lr) {
            access = lr.token();
            refresh = lr.refreshToken();
        }
        if (access != null && !access.isBlank()) {
            builder.header("Set-Cookie", CookieAuthSupport.build(
                    CookieAuthSupport.ACCESS_COOKIE, access, "/", cookieSecure,
                    CookieAuthSupport.SESSION_MAX_AGE));
        }
        if (refresh != null && !refresh.isBlank()) {
            builder.header("Set-Cookie", CookieAuthSupport.build(
                    CookieAuthSupport.REFRESH_COOKIE, refresh,
                    CookieAuthSupport.REFRESH_PATH, cookieSecure,
                    CookieAuthSupport.SESSION_MAX_AGE));
        }
        return builder.body(body);
    }

    @PostMapping("/set-password")
    public ResponseEntity<?> setPassword(@Valid @RequestBody SetPasswordRequest request,
                                          HttpServletRequest servletRequest) {
        PasswordOutcome outcome = setPasswordHandler.handle(
                new SetPasswordCommand(request.email(), request.newPassword(), request.currentPassword(),
                        servletRequest.getRemoteAddr()));
        return mapPasswordOutcome(outcome);
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                             @AuthenticationPrincipal UserPrincipal principal,
                                             HttpServletRequest servletRequest) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PasswordOutcome outcome = changePasswordHandler.handle(new ChangePasswordCommand(
                principal.getUserIdAsUuid(), request.newPassword(), request.currentPassword(),
                request.totpCode(), servletRequest.getRemoteAddr()));
        return mapPasswordOutcome(outcome);
    }

    private ResponseEntity<?> mapPasswordOutcome(PasswordOutcome outcome) {
        return switch (outcome.status()) {
            case SUCCESS -> withCookies(outcome.body(), 200);
            case USER_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(outcome.body());
            case USER_GONE -> ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            case INACTIVE, MFA_NOT_CONFIGURED, MFA_ACCOUNT_LOCKED -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(outcome.body());
            case MFA_ENROLLMENT_REQUIRED -> ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(outcome.body());
            case CURRENT_PASSWORD_INCORRECT, TOTP_INVALID -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(outcome.body());
            case CURRENT_PASSWORD_REQUIRED, UNEXPECTED_CURRENT_PASSWORD -> ResponseEntity.badRequest()
                    .body(outcome.body());
        };
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
                                    HttpServletRequest servletRequest,
                                    HttpServletResponse servletResponse) {
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
        CookieAuthSupport.clear(servletResponse, cookieSecure);
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
        List<com.aircargo.authservice.dto.SiteDTO> userSites;
        if (user.getRole() == UserRole.SUPER_USER && user.getSites().isEmpty()) {
            userSites = siteRepository.findByIsActiveTrue().stream()
                    .map(com.aircargo.authservice.dto.SiteDTO::fromEntity)
                    .collect(Collectors.toList());
        } else {
            userSites = user.getSites().stream()
                    .map(com.aircargo.authservice.dto.SiteDTO::fromEntity)
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
        user.setTokensValidFrom(java.time.OffsetDateTime.now());  // mata sesiones activas del bloqueado
        userRepository.save(user);
        tokenRevocationService.evict(userId);
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

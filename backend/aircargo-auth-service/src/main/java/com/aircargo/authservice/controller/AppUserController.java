package com.aircargo.authservice.controller;

import com.aircargo.common.auth.UserPrincipal;
import com.aircargo.authservice.dto.AppUserDTO;
import com.aircargo.authservice.dto.ConnectedUserDTO;
import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.service.ActiveSessionTracker;
import com.aircargo.authservice.service.AppUserService;
import com.aircargo.authservice.service.AuditService;
import com.aircargo.authservice.service.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class AppUserController {

    private final AppUserService appUserService;
    private final AuditService auditService;
    private final ActiveSessionTracker sessionTracker;
    private final MfaService mfaService;
    private final AppUserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final com.aircargo.authservice.service.PasswordResetService passwordResetService;
    private final com.aircargo.authservice.service.TokenRevocationService tokenRevocationService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public AppUserController(AppUserService appUserService, AuditService auditService,
                             ActiveSessionTracker sessionTracker, MfaService mfaService,
                             AppUserRepository userRepository,
                             com.aircargo.authservice.service.PasswordResetService passwordResetService,
                             com.aircargo.authservice.service.TokenRevocationService tokenRevocationService) {
        this.appUserService = appUserService;
        this.auditService = auditService;
        this.sessionTracker = sessionTracker;
        this.mfaService = mfaService;
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.passwordResetService = passwordResetService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @GetMapping
    public List<AppUserDTO> getAll(@RequestParam(required = false) UUID airlineId) {
        return appUserService.getAll(airlineId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppUserDTO> getById(@PathVariable UUID id) {
        return appUserService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AppUserDTO> create(@Valid @RequestBody AppUserDTO dto,
                                              @AuthenticationPrincipal UserPrincipal principal,
                                              HttpServletRequest request) {
        if (dto.getSiteIds() == null || dto.getSiteIds().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        AppUserDTO created = appUserService.create(dto);
        auditService.logUserCreate(
                principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                created.getId(), created.getEmail(), request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppUserDTO> update(@PathVariable UUID id, @Valid @RequestBody AppUserDTO dto,
                                              @AuthenticationPrincipal UserPrincipal principal,
                                              HttpServletRequest request) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return appUserService.update(id, dto)
                .map(updated -> {
                    auditService.logUserUpdate(
                            principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                            id, "{\"role\":\"" + dto.getRole() + "\",\"isActive\":" + dto.getIsActive() + "}",
                            request.getRemoteAddr());
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest request) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        AppUserDTO existing = appUserService.getById(id).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();
        if (existing.getId().equals(principal.getUserIdAsUuid())) {
            return ResponseEntity.badRequest().build();
        }
        boolean removed = appUserService.delete(id);
        if (!removed) return ResponseEntity.notFound().build();
        auditService.logUserDelete(
                principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                id, existing.getEmail(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserPrincipal principal,
                                            HttpServletRequest request) {
        AppUserDTO user = appUserService.getById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        appUserService.resetPassword(id);
        tokenRevocationService.bump(id);  // la contraseña fue borrada → matar sesiones
        auditService.logPasswordReset(
                principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                id, user.getEmail(), request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "Contraseña restablecida"));
    }

    @GetMapping("/connected")
    public List<ConnectedUserDTO> getConnected() {
        return sessionTracker.getConnectedUsers();
    }

    @PostMapping("/{id}/mfa/setup")
    public ResponseEntity<?> setupMfa(@PathVariable UUID id) {
        AppUserDTO user = appUserService.getById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
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

    @PostMapping("/{id}/mfa/enable")
    public ResponseEntity<?> enableMfa(@PathVariable UUID id,
                                        @RequestBody Map<String, String> body,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest request) {
        AppUserDTO user = appUserService.getById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        String secret = body.get("secret");
        String totpCode = body.get("totpCode");
        if (secret == null || totpCode == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "secret y totpCode son requeridos"));
        }
        if (!mfaService.verifyCode(secret, totpCode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Código TOTP inválido"));
        }
        mfaService.enableMfa(id, secret);
        tokenRevocationService.bump(id);
        auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                "MFA_ENABLED", "USER", id.toString(), null, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "MFA habilitado correctamente"));
    }

    @PostMapping("/{id}/mfa/disable")
    public ResponseEntity<?> disableMfa(@PathVariable UUID id,
                                         @AuthenticationPrincipal UserPrincipal principal,
                                         HttpServletRequest request) {
        AppUserDTO user = appUserService.getById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        mfaService.disableMfa(id);
        tokenRevocationService.bump(id);
        auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                "MFA_DISABLED", "USER", id.toString(), null, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "MFA deshabilitado"));
    }

    @PostMapping("/{id}/mfa/lock")
    public ResponseEntity<?> lockMfa(@PathVariable UUID id,
                                      @AuthenticationPrincipal UserPrincipal principal,
                                      HttpServletRequest request) {
        AppUserDTO user = appUserService.getById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        mfaService.lockMfa(id);
        auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                "MFA_LOCKED", "USER", id.toString(), null, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "Cuenta bloqueada"));
    }

    @PostMapping("/{id}/mfa/unlock")
    public ResponseEntity<?> unlockMfa(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest request) {
        AppUserDTO user = appUserService.getById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        mfaService.unlockMfa(id);
        auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                "MFA_UNLOCKED", "USER", id.toString(), null, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "Cuenta desbloqueada"));
    }

    @PostMapping("/{id}/generate-reset-link")
    public ResponseEntity<?> generateResetLink(@PathVariable UUID id,
                                               @AuthenticationPrincipal UserPrincipal principal,
                                               HttpServletRequest request) {
        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        String token = passwordResetService.create(user.getId());
        String link = frontendUrl + "/set-password?token=" + token;

        auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                com.aircargo.authservice.event.AuditEventType.PASSWORD_RESET, "USER", id.toString(),
                "{\"email\":\"" + user.getEmail() + "\"}", request.getRemoteAddr());

        // El enlace se muestra UNA vez: da acceso a definir contraseña por 15 min y muere al usarse.
        // Nadie conoce la contraseña excepto el usuario que la escribe.
        return ResponseEntity.ok(java.util.Map.of(
                "resetLink", link,
                "expiresMinutes", 15,
                "message", "Enlace de un solo uso válido por 15 minutos. Envíeselo al usuario; expira o muere al usarse."
        ));
    }

}

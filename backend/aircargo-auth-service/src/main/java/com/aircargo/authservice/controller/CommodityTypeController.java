package com.aircargo.authservice.controller;

import com.aircargo.authservice.dto.CommodityTypeDTO;
import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.service.AuditService;
import com.aircargo.authservice.service.CommodityTypeService;
import com.aircargo.authservice.service.MfaService;
import com.aircargo.common.auth.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/commodity-types")
public class CommodityTypeController {

    private final CommodityTypeService service;
    private final AppUserRepository userRepository;
    private final MfaService mfaService;
    private final AuditService auditService;

    public CommodityTypeController(CommodityTypeService service,
                                   AppUserRepository userRepository,
                                   MfaService mfaService,
                                   AuditService auditService) {
        this.service = service;
        this.userRepository = userRepository;
        this.mfaService = mfaService;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<CommodityTypeDTO>> getAll(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(service.getAll(activeOnly));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommodityTypeDTO> getById(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<CommodityTypeDTO> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(service.getByCode(code));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body,
                                    @AuthenticationPrincipal UserPrincipal principal,
                                    @RequestHeader(value = "X-TOTP-Token", required = false) String totpToken,
                                    HttpServletRequest servletRequest) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String totpError = verifyTotp(principal, totpToken);
        if (totpError != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", totpError));
        }

        String code = (String) body.get("code");
        String label = (String) body.get("label");
        String description = (String) body.get("description");
        String color = (String) body.get("color");
        Integer sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : 0;
        Boolean isActive = body.get("isActive") != null ? (Boolean) body.get("isActive") : true;

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "code is required"));
        }
        try {
            CommodityTypeDTO dto = service.create(code, label, description, color, sortOrder, isActive);
            auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                "COMMODITY_TYPE_CREATED", "COMMODITY_TYPE", dto.id().toString(),
                "code=" + code, servletRequest.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable java.util.UUID id,
                                    @RequestBody Map<String, Object> body,
                                    @AuthenticationPrincipal UserPrincipal principal,
                                    @RequestHeader(value = "X-TOTP-Token", required = false) String totpToken,
                                    HttpServletRequest servletRequest) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String totpError = verifyTotp(principal, totpToken);
        if (totpError != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", totpError));
        }

        String code = (String) body.get("code");
        String label = (String) body.get("label");
        String description = (String) body.get("description");
        String color = (String) body.get("color");
        Integer sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : null;
        Boolean isActive = body.get("isActive") != null ? (Boolean) body.get("isActive") : null;

        try {
            CommodityTypeDTO dto = service.update(id, code, label, description, color, sortOrder, isActive);
            auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                "COMMODITY_TYPE_UPDATED", "COMMODITY_TYPE", id.toString(),
                "code=" + dto.code(), servletRequest.getRemoteAddr());
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable java.util.UUID id,
                                    @AuthenticationPrincipal UserPrincipal principal,
                                    @RequestHeader(value = "X-TOTP-Token", required = false) String totpToken,
                                    HttpServletRequest servletRequest) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String totpError = verifyTotp(principal, totpToken);
        if (totpError != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", totpError));
        }

        try {
            service.delete(id);
            auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                "COMMODITY_TYPE_DELETED", "COMMODITY_TYPE", id.toString(),
                null, servletRequest.getRemoteAddr());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/restore-defaults")
    public ResponseEntity<?> restoreDefaults(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "X-TOTP-Token", required = false) String totpToken,
            HttpServletRequest servletRequest) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String totpError = verifyTotp(principal, totpToken);
        if (totpError != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", totpError));
        }

        try {
            int count = service.resetToDefaults();
            auditService.log(principal.getUserIdAsUuid(), principal.email(), principal.fullName(),
                "COMMODITY_TYPE_RESTORE_DEFAULTS", "COMMODITY_TYPE", null,
                "restored=" + count, servletRequest.getRemoteAddr());
            return ResponseEntity.ok(Map.of("restored", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String verifyTotp(UserPrincipal principal, String totpToken) {
        AppUser user = userRepository.findById(principal.getUserIdAsUuid()).orElse(null);
        if (user == null) return "Usuario no encontrado";

        boolean mfaEnabled = Boolean.TRUE.equals(user.getMfaEnabled());
        if (!mfaEnabled || user.getMfaSecret() == null) {
            return "Debes configurar autenticación de dos factores (MFA) antes de gestionar commodities";
        }
        if (Boolean.TRUE.equals(user.getMfaLocked())) {
            return "Cuenta bloqueada por intentos fallidos de MFA";
        }
        if (totpToken == null || totpToken.isBlank()) {
            return "Se requiere código TOTP (X-TOTP-Token header)";
        }
        if (!mfaService.verifyCode(user.getMfaSecret(), totpToken)) {
            return "Código TOTP inválido";
        }
        return null;
    }
}

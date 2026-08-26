package com.aircargo.authservice.controller;

import com.aircargo.authservice.dto.BackupDTOs;
import com.aircargo.authservice.service.BackupConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupConfigController {

    private final BackupConfigService service;

    @GetMapping("/config")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_USER')")
    public ResponseEntity<BackupDTOs.BackupConfigDTO> getConfig() {
        return ResponseEntity.ok(service.getConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_USER')")
    public ResponseEntity<BackupDTOs.BackupConfigDTO> updateConfig(@Valid @RequestBody BackupDTOs.BackupConfigDTO dto) {
        return ResponseEntity.ok(service.updateConfig(dto));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_USER')")
    public ResponseEntity<BackupDTOs.BackupStatsDTO> getStats() {
        return ResponseEntity.ok(service.getStats());
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_USER')")
    public ResponseEntity<List<BackupDTOs.BackupHistoryDTO>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getHistory(page, size));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_USER')")
    public ResponseEntity<BackupDTOs.BackupHistoryDTO> getLatestBackup() {
        return service.getLatestBackup()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/latest-pre-deploy")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_USER')")
    public ResponseEntity<BackupDTOs.BackupHistoryDTO> getLatestPreDeployBackup() {
        return service.getLatestPreDeployBackup()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/trigger")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_USER')")
    public ResponseEntity<String> triggerBackup(@RequestParam(defaultValue = "MANUAL") String type) {
        try {
            // El script recibe el tipo sin guiones: daily|pre-deploy|post-deploy|manual
            String t = type.toLowerCase().replace("_", "-").replace("--", "");
            java.io.File script = resolveScript();
            if (!script.isFile()) {
                return ResponseEntity.internalServerError()
                    .body("No se encontró scripts/db-backup.sh — ejecuta desde la raíz del proyecto");
            }
            ProcessBuilder pb = new ProcessBuilder(script.getAbsolutePath(), t)
                .directory(script.getParentFile().getParentFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes();
            int code = p.waitFor();
            if (code != 0) {
                return ResponseEntity.internalServerError().body("El backup terminó con código " + code);
            }
            return ResponseEntity.ok("Backup " + t + " completado");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    /** Localiza scripts/db-backup.sh subiendo directorios desde user.dir (el jar puede correr desde cualquier cwd). */
    private static java.io.File resolveScript() {
        java.io.File dir = new java.io.File(System.getProperty("user.dir")).getAbsoluteFile();
        for (int i = 0; i < 6 && dir != null; i++) {
            java.io.File candidate = new java.io.File(dir, "scripts/db-backup.sh");
            if (candidate.isFile()) return candidate;
            dir = dir.getParentFile();
        }
        return new java.io.File("scripts/db-backup.sh");
    }
}
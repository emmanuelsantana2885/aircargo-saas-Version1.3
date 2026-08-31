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
            java.io.File script = resolveScript("db-backup.sh");
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

    @PostMapping("/restore")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_USER')")
    public ResponseEntity<BackupDTOs.RestoreResult> restore(@RequestBody BackupDTOs.RestoreRequest req) {
        try {
            // Validación de la fuente (anti-SSRF: solo http/https para URLs)
            if (req == null || (req.getSource() == null && req.getUrl() == null && req.getFilePath() == null)) {
                return ResponseEntity.badRequest().body(BackupDTOs.RestoreResult.builder()
                    .success(false).message("Indica source + filePath para copia local, o url para nube").build());
            }
            String source = req.getSource() == null ? (req.getUrl() != null ? "url" : "local") : req.getSource();
            java.io.File script = resolveScript("db-restore.sh");
            if (!script.isFile()) {
                return ResponseEntity.internalServerError().body(BackupDTOs.RestoreResult.builder()
                    .success(false).message("No se encontró scripts/db-restore.sh — ejecuta desde la raíz del proyecto").build());
            }

            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(script.getAbsolutePath());
            if ("url".equalsIgnoreCase(source)) {
                String url = req.getUrl();
                if (url == null || url.isBlank()) {
                    return ResponseEntity.badRequest().body(BackupDTOs.RestoreResult.builder()
                        .success(false).message("source=url requiere el parámetro 'url'").build());
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return ResponseEntity.badRequest().body(BackupDTOs.RestoreResult.builder()
                        .success(false).message("La URL debe ser http(s):// (no se permiten file:// ni protocolos locales)").build());
                }
                cmd.add("--url");
                cmd.add(url);
            } else {
                String path = req.getFilePath();
                if (path == null || path.isBlank()) {
                    return ResponseEntity.badRequest().body(BackupDTOs.RestoreResult.builder()
                        .success(false).message("source=local requiere el parámetro 'filePath'").build());
                }
                cmd.add("--file");
                cmd.add(path);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(script.getParentFile().getParentFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int code = p.waitFor();

            return ResponseEntity.ok(BackupDTOs.RestoreResult.builder()
                .success(code == 0)
                .exitCode(code)
                .dumpPath(out.lines().filter(l -> l.startsWith("OK:")).map(l -> l.substring(3).trim()).findFirst().orElse(null))
                .message(out.trim().lines()
                    .filter(l -> l.contains("✅") || l.contains("❌") || l.contains("⚠️"))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(code == 0 ? "Restauración completada" : "Restauración fallida (código " + code + ")"))
                .build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(BackupDTOs.RestoreResult.builder()
                .success(false).message("Error: " + e.getMessage()).build());
        }
    }

    /** Localiza scripts/<nombre> subiendo directorios desde user.dir (el jar puede correr desde cualquier cwd). */
    private static java.io.File resolveScript(String name) {
        java.io.File dir = new java.io.File(System.getProperty("user.dir")).getAbsoluteFile();
        for (int i = 0; i < 6 && dir != null; i++) {
            java.io.File candidate = new java.io.File(dir, "scripts/" + name);
            if (candidate.isFile()) return candidate;
            dir = dir.getParentFile();
        }
        return new java.io.File("scripts/" + name);
    }
}
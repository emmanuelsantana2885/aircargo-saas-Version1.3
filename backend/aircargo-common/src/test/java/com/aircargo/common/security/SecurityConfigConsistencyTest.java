package com.aircargo.common.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GUARD DE CONSISTENCIA DE SEGURIDAD (regresión definitiva).
 *
 * Escanea el SecurityConfig de CADA microservicio y falla si alguno no cumple
 * los invariantes acordados. Esto existe porque en producción un servicio
 * respondía 403 (entry point por defecto) ante peticiones sin autenticación
 * mientras los demás devolvían 401 — rompiendo el refresh transparente del
 * frontend. El drift entre configs no volverá a pasar sin que el build falle.
 *
 * Invariantes por servicio (con BD):
 *  I1. HttpStatusEntryPoint(UNAUTHORIZED) — nunca 403 para anónimos.
 *  I2. JwtAuthFilter construido con (jwtUtil, jdbcTemplate) — revocación central.
 * Invariantes load-planning (sin datasource):
 *  I3. JwtAuthFilter(jwtUtil) y SIN entry point de cookie… usa 401 entry point igualmente.
 */
class SecurityConfigConsistencyTest {

    private static final Path BACKEND = resolverBackend();

    /** Funciona tanto si el build corre desde la raíz como desde el módulo. */
    private static Path resolverBackend() {
        for (Path c : List.of(Path.of("..", "backend"), Path.of("backend"), Path.of("..", "..", "backend"))) {
            Path p = c.toAbsolutePath().normalize();
            if (Files.isDirectory(p.resolve("aircargo-common"))) return p;
        }
        throw new IllegalStateException("No se encontró el directorio backend/");
    }

    /** Servicios con datasource: revocación central obligatoria. */
    private static final List<String> WITH_DB = List.of(
            "aircargo-auth-service", "aircargo-flight-service", "aircargo-booking-service",
            "aircargo-mawb-service", "aircargo-warehouse-service", "aircargo-uld-service",
            "aircargo-export-service", "aircargo-notification-service");

    /** Servicio stateless (sin BD): filtro sin jdbcTemplate pero mismo entry point. */
    private static final String STATELESS = "aircargo-load-planning-service";

    @Test
    void todosLosSecurityConfigsCumplenLosInvariantes() throws IOException {
        List<String> violations = new ArrayList<>();
        int checked = 0;

        try (Stream<Path> modules = Files.list(BACKEND)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                String name = module.getFileName().toString();
                if (!name.startsWith("aircargo-") || name.endsWith("-common")
                        || name.endsWith("gateway") || name.endsWith("feign-clients")) continue;

                Path cfg = findSecurityConfig(module);
                if (cfg == null) { violations.add(name + ": no tiene SecurityConfig"); continue; }
                checked++;
                String src = Files.readString(cfg);

                // I1 — entry point 401 para anónimos (nunca 403)
                boolean epOk = src.contains("HttpStatusEntryPoint")
                        && src.contains("HttpStatus.UNAUTHORIZED");
                if (!epOk) {
                    violations.add(name + ": falta HttpStatusEntryPoint(UNAUTHORIZED) → anónimos reciben 403");
                }
                // I2/I3 — wiring del filtro según tenga o no BD
                if (WITH_DB.contains(name)) {
                    if (!src.contains("new JwtAuthFilter(jwtUtil, jdbcTemplate)")) {
                        violations.add(name + ": JwtAuthFilter sin jdbcTemplate → sin revocación central por-request");
                    }
                    if (!src.contains("JdbcTemplate jdbcTemplate")) {
                        violations.add(name + ": filterChain no inyecta JdbcTemplate");
                    }
                } else if (name.equals(STATELESS)) {
                    if (!src.contains("new JwtAuthFilter(jwtUtil)")) {
                        violations.add(name + ": estado sin BD debe usar JwtAuthFilter(jwtUtil)");
                    }
                }
                // I4 — nada de CORS permisivo heredado
                if (src.contains("@CrossOrigin")) {
                    violations.add(name + ": @CrossOrigin prohibido (usa SecurityConfig/CORS_ORIGINS)");
                }
            }
        }

        assertTrue(checked >= 9, "Se esperaban ≥9 módulos revisados, encontrados: " + checked);
        assertTrue(violations.isEmpty(),
                "Configuraciones de seguridad desalineadas:\n  - " + String.join("\n  - ", violations));
    }

    private Path findSecurityConfig(Path module) throws IOException {
        try (Stream<Path> walk = Files.walk(module)) {
            return walk.filter(p -> p.getFileName().toString().equals("SecurityConfig.java")).findFirst().orElse(null);
        }
    }
}

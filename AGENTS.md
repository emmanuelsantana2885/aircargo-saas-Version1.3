# Aircargo — agent notes

## Structure

Monorepo with three main directories + microservices scaffolding:

| Dir | Stack | Entrypoint / notes |
|-----|-------|-------------------|
| `frontend/` | Vue 3 + Vite + Pinia + Vue Router + Tailwind + JS | Vite dev on port 5173, proxy `/api` → `localhost:8080` (gateway) |
| `backend/aircargo-common/` | Shared entities, JWT, DTOs, PageResponse | Used by all backend modules |
| `backend/aircargo-feign-clients/` | Shared Feign client interfaces + DTOs | AuthClient, FlightClient, MawbClient, BookingClient, UldClient |
| `backend/aircargo-gateway/` | Spring Cloud Gateway (hardened) | JWT auth, rate limiting, circuit breaker, CORS, access logging, Swagger aggregation. Routes to all 9 services. |
| `backend/aircargo-auth-service/` | Spring Boot (port 9092) | Auth, User, Site, Audit, MFA, RolePermission |
| `backend/aircargo-flight-service/` | Spring Boot (port 9093) | Flight CRUD + Airlines + AircraftTypes |
| `backend/aircargo-booking-service/` | Spring Boot (port 9094) | Booking CRUD + AWB assignment |
| `backend/aircargo-mawb-service/` | Spring Boot (port 9095) | MAWB + HAWB + DUA (Compliance) CRUD |
| `backend/aircargo-warehouse-service/` | Spring Boot (port 9096) | Warehouse receipts + PDF/Excel exports + supporting docs + audit logging |
| `backend/aircargo-uld-service/` | Spring Boot (port 9097) | ULD/ULD-AWB/ULD-Piece CRUD + barcode scanning + ULD transfer + SSE events |
| `backend/aircargo-load-planning-service/` | Spring Boot (port 9098) | Load planning + batch import + export manifest + pallet sheets |
| `backend/aircargo-export-service/` | Spring Boot (port 9099) | Read-only analytics: Export, BI, Reports, Catalog |
| `backend/aircargo-notification-service/` | Spring Boot (port 9100) | Notifications + RabbitMQ event listeners + email |
| `database/migrations/` | PostgreSQL Flyway migrations | Root copy — see "Migrations" below |
| `docker/` | Docker Compose files | `docker-compose.infrastructure.yml` (Postgres+RabbitMQ), `docker-compose.services.yml` (9 services + gateway) |
| `k8s/` | Kubernetes manifests | Full K8s deployment for all services. **`secret.yml` NO vive aquí** — está en `~/Desktop/Projects/Rannik/aircargo-deploy-secrets/secret.yml` (fuera del repo, gitignore lo bloquea). Para desplegar: `kubectl apply -f k8s/ -f ../aircargo-deploy-secrets/secret.yml` (el Secret se llama `aircargo-secrets` y los manifests lo referencian por nombre, no por ruta) |

## Commands

```sh
# Full stack (Postgres + RabbitMQ + 9 services + gateway + frontend)
./start-all.sh            # build + start everything (needs Docker for infra)
./start-all.sh --skip-build   # same, reuse existing jars (fast boot)
tail -f /tmp/aircargo-gateway.log   # logs per service in /tmp/<name>.log
# Backend only (staggered boot with health waits)
./start-backend.sh

# Frontend
npm install          # in frontend/
npm run dev          # Vite dev server (port 5173)
npm run lint         # ESLint (flat config, no --ext)
npm run build        # Vite build

# Backend
mvn test                               # unit + integration tests (H2, Flyway disabled)
mvn spring-boot:run                    # starts on port 9091 (needs Postgres)
mvn clean compile spring-boot:run      # full rebuild + run

# Database
docker compose up -d                   # PostgreSQL 16 alpine on :5432
```

### Environment & tooling

- Secrets live in a gitignored root `.env` (copy `.env.example`, fill real values). Required: `JWT_SECRET` (min 32 chars), `POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`. `aircargo-env.sh` validates them and exports everything for the start scripts.
- Maven is auto-detected: `MAVEN_BIN` env var → `mvn` on PATH → IntelliJ bundled Maven (flatpak) → SDKMAN. All start scripts use `$MAVEN_BIN`, so no manual PATH setup is needed.
- `start-all.sh` requires Docker for the Postgres+RabbitMQ containers; if they're already up (native or containerized), Docker is not touched.

## Hard-coded constants

- UPS airline UUID `00000000-0000-0000-0000-000000000001` is the seeded reference airline (flight-service `V1__init.sql`, root copy `V1__init.sql`). The frontend NO LONGER hardcodes it — airlines load from the API (`/api/airlines`). Keep the seed in sync with any hardcoded references in code.
- **Master data seeding (auth-service `DataSeeder`)**: `com.aircargo.authservice.config.DataSeeder` (`@Profile("!test")`, `ApplicationRunner`) seeds the UPS airline, sites (SDQ/STI/PUJ/MIA), users, site assignments, and view/role permissions idempotently on startup — required because auth-service has `spring.flyway.enabled=false` + `ddl-auto=update` (Hibernate creates tables but never seeds). This is what makes `./start-all.sh` work on a fresh database (no `database/migrations/` seed files are ever applied by any service). It uses `AirlineRepository` (new, in auth-service) for the shared `com.aircargo.common.entity.Airline`.
- **Additional airlines** (FDX/DHL/M6/UC/QT/5Y/K4/GG/M7) live in flight-service `V2__seed_extra_airlines.sql` (migrated from root copy `V12__seed_more_airlines.sql`, `ON CONFLICT DO NOTHING`).
- Vite proxy in `frontend/vite.config.js` assumes backend is on `localhost:8080` (gateway).

## Migrations

Flyway migrations live in **each microservice** at `backend/aircargo-*-service/src/main/resources/db/migration/`. They are the source of truth for that service's schema.

**Root copy:** `database/migrations/` — contains all migrations combined with full Postgres schema (functions, triggers, permissions). Keep in sync with each service.

## Microservices Migration Status

| Phase | Service | Status |
|-------|---------|--------|
| Phase | Service | Status |
|-------|---------|--------|
| Phase 1 | Gateway Hardening | ✅ Complete — JWT filter, rate limiting, circuit breaker, CORS |
| Phase 2 | Auth Service | ✅ Complete — refresh tokens, lockout, Feign clients, schema migration |
| Phase 3 | Flight Service | ✅ Complete — PageResponse, @Transactional, AircraftType endpoint, cache config |
| Phase 4 | Booking Service | ✅ Complete — controller API match, airlineId/flightId filter, PageResponse, @Transactional |
| Phase 5 | MAWB Service | ✅ Complete — DUA compliance merged in, PageResponse, @Transactional, supporting docs, SecurityConfig, CacheConfig |
| Phase 6 | Warehouse Service | ✅ Complete — all services extracted, audited, wired; supporting PDF/Excel/PdfGeneration services adapted; audit logging added to both controllers; frontend proxy → gateway |
| Phase 7 | ULD Service | ✅ Complete — all entities, DTOs, repositories, services, controllers migrated; Feign clients for MAWB/booking lookup; SSE scan events; Caffeine cache; Flyway migration; SecurityConfig with JWT + CORS; gateway routes |
| Phase 8 | Load Planning Service | ✅ Complete — stateless service via Feign, batch import, ramp manifest parser, export manifest, pallet sheets |
| Phase 9 | Export/BI Service | ✅ Complete — read-only analytics on port 9099; 4 controllers (Export, BI, Reports, Catalog); 11 read-only JPA entities; 12 BI aggregation endpoints |
| Phase 10 | Notification Service | ✅ Complete — RabbitMQ event listeners, CRUD notifications, email stubs, AuthClient Feign |
| Phase 11 | Frontend Migration | ✅ Complete — proxy target to gateway (8080), all API paths remain unchanged |
| Phase 12 | Delete Monolith | ✅ Complete — `backend/aircargo-api/` removed, DUA compliance migrated to mawb-service, gateway routes cleaned, Swagger/OpenAPI added to all services |

Full plan: `Documents/MICROSERVICES-MIGRATION-PLAN.md`

## Recent session changes (Aug 22, 2026 (3) — secret.yml fuera del repo)
`k8s/secret.yml` (manifest K8s con placeholders `${POSTGRES_USER}/${POSTGRES_PASSWORD}/${JWT_SECRET}`, sin valores reales) movido a `~/Desktop/Projects/Rannik/aircargo-deploy-secrets/secret.yml` — carpeta hermana FUERA del proyecto. `.gitignore` ahora bloquea `k8s/secret*.yml` para evitar re-creación accidental. Los demás manifests de `k8s/` referencian el Secret por nombre (`aircargo-secrets`) así que no requieren cambios; al desplegar hay que aplicar la carpeta externa además de `k8s/`. Motivación: Graphify lo marcó como archivo potencialmente sensible durante el indexado del grafo.

## Recent session changes (Aug 23, 2026 (21) — Fix i18n linked-format + 428 silencioso)
- **`@` literal en mensajes i18n rompe la compilación** (vue-i18n lo interpreta como linked message `@:key`): `emailPlaceholder` ('usuario@aircargo.com') y regla de contraseña '(!@#$...)' escapados con `{'@'}`. Era el spam "Invalid linked format" de consola.
- Clave faltante `ulds.pcsProgress` ({assigned}/{received}) añadida es/en; escaneo global de claves: 0 faltantes.
- `client.js`: 428 no dispara toast global (LoginView maneja MFA/contraseña inline) — eliminaba toasts duplicados en el login de dos pasos.
- Refresh por cookie verificado E2E (login→refresh→nuevo access→200). El 401 tras inactividad >1h es esperado: el interceptor refresca automáticamente; si el refresh falla (sesión revocada/rt ausente) redirige a /login.

## Recent session changes (Aug 23, 2026 (20) — Corrección definitiva de la clase 403/401 + guard de entrega)
**Causa raíz de los 403 masivos**: solo booking-service tenía `HttpStatusEntryPoint(UNAUTHORIZED)`; los demás servicios devolvían **403** a peticiones anónimas (entry point por defecto de Spring Security), así que el refresh transparente del frontend (que se dispara con 401) nunca actuaba al expirar la cookie.
- **Fix aplicado en los 9 SecurityConfigs**: `.exceptionHandling(eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED)))` — anónimo = 401 siempre; verificado: /api/users, /flights/list, /ulds, /mawbs, load-planning → 401 ✓.
- **`SecurityConfigConsistencyTest`** (common): guard que escanea el SecurityConfig de cada módulo y falla el build si falta el entry point 401, si un servicio con BD no pasa jdbcTemplate al JwtAuthFilter (revocación por-request), o si reaparece @CrossOrigin. Load-planning (sin BD) validado con filtro de un argumento. Acepta import FQN o estático. Este tipo de desalineación ya no puede llegar a producción sin romper el build.
- **`scripts/smoke-e2e.sh`**: batería de entrega con 22 comprobaciones (infra, health×9, login inválido→401 genérico, anónimo→401 en endpoints clave, login real→cookies httpOnly→acceso autorizado, Vite). Uso: `ADMIN_EMAIL=… ADMIN_PASS=… ./scripts/smoke-e2e.sh`. Exit 0 = entregable.
- Regresión final: reactor **86 tests** BUILD SUCCESS (common 16 incl. guard, auth 23, flight 11, booking 7, mawb 11, warehouse 4, uld 11, gateway 3); frontend lint 0 errores, build OK, vitest 8/8; SMOKE E2E 22/22.

## Recent session changes (Aug 23, 2026 (19) — Residuales R1+R2: Cookies httpOnly + revocación en todos los servicios)
**R1 — Tokens fuera de localStorage (mitiga XSS):**
- `common/auth/CookieAuthSupport`: `aircargo_at` (Path=/, 1h) y `aircargo_rt` (Path=/api/auth, 7d), httpOnly + SameSite=Lax + Secure configurable (`app.jwt.cookie-secure` / env `COOKIE_SECURE`, true en k8s configmap).
- `AuthController`: emite cookies en login/refresh/set-password-token/change-password (`withCookies()` soporta Map y LoginResponse); logout las limpia; refresh lee el token de la cookie o body por compatibilidad.
- Frontend: `client.js` ya no inyecta Authorization; refresh transparente vía cookie con single-flight; `stores/auth.js` persiste SOLO perfil no sensible; router guarda por userId+site. EventSource/SSE ahora autentica por cookie (mejora).
**R2 — Revocación por-request en TODOS los servicios con BD:**
- `JwtAuthFilter` común acepta JdbcTemplate opcional: valida tokens_valid_from/blocked/is_active por request (caché 30s). Los 8 SecurityConfigs pasan jdbcTemplate (load-planning sin BD queda stateless, documentado). TokenRevocationFilter de auth eliminado (sustituido); TokenRevocationService se conserva para bumps y refresh.
- Gateway también acepta cookie (MultiValueMap getFirst).
- **E2E**: login→2 cookies ✓ · /api/users y /api/flights/list solo con cookie→200 ✓ · bloqueo→401 Session revoked en flight-service ✓. Lint/build/tests OK.
- Lección de shell: pkill -f con patrón regex se auto-mata si el propio comando contiene el literal en otra parte — separar kill y build en comandos distintos.

## Recent session changes (Aug 23, 2026 (18) — Mayor D resuelta: Logging con rotación)
- Los 10 servicios ahora escriben a `~/aircargo-logs/<nombre>.log` vía política nativa Logback de Spring Boot (sin XML): **10 MB/archivo, 14 días de histórico, cap total 200 MB**, rotados comprimidos `.gz`. Redirigible con `LOG_DIR` en despliegue.
- `start-all.sh` ya no acumula stdout en `/tmp` (`> /dev/null`); los hints de tail-logs apuntan a la nueva ruta. El fallback de diagnóstico del gateway mantiene lectura de /tmp como segunda opción.
- Centralización mínima en single-host = un solo directorio con todos los logs; para multi-instancia se recomienda Loki/ELK (fuera de alcance actual).
- Verificado: stack completo relanzado, 10 archivos creados y escribiendo, login E2E OK.

## Recent session changes (Aug 23, 2026 (17) — Mayor C resuelta: Frontend resiliente + tests)
- **`components/ErrorBoundary.vue`**: captura errores de render de las vistas (`onErrorCaptured` → fallback con título/mensaje/Reintentar/Inicio; navegar limpia el estado). Envuelve ambos `router-view` en App.vue. `main.js`: `app.config.errorHandler` global como última red.
- **Infra de tests (antes cero)**: vitest + @vue/test-utils + happy-dom; script `npm test`; bloque `test:` en vite.config.js. Tests en `frontend/tests/*.spec.js`.
- **Tests**: ErrorBoundary 3 (sano, crash→fallback, recover) y LocaleDatePicker 5 — **regresión de los bugs reales**: 12 meses en dropdown (t() no devuelve arrays), mes/año actual seleccionados, emisión ISO, botón Hoy, display "15 Ago 2026". Intl devuelve meses en minúscula → capitalizados ahora en el componente.
- Lección: `vite.config.js` editado con python dejó `,` duplicada y rompió el arranque de Vitest — validar sintaxis tras ediciones programáticas.

## Recent session changes (Aug 23, 2026 (16) — Mayor B resuelta: Auditoría sin pérdida sin broker)
**Antes**: booking/mawb/warehouse/flight publicaban auditoría por AMQP fire-and-forget — sin broker los eventos se perdían silenciosamente, y con broker iban a `notification.audit_log`, que el query-side de auth NO lee.
**Ahora** (`com.aircargo.common.audit.AuditService` reescrito):
1. **PRIMARY: INSERT directo en `audit_log` compartida** (JdbcTemplate opcional) — visible al instante en Seguridad vía el merge existente del query side.
2. **SECUNDARIO best-effort**: publish AMQP para consumidores en tiempo real; su fallo no afecta integridad.
Sin BD ni broker: log + descarte explícito (nunca lanza). IP pseudonimizada en ambas rutas.
- Tests: AuditServiceTest 5 casos con fakes manuales (byte-buddy no mockea clases concretas en JDK 25 — patrón FakeJdbc/FakeRabbit para el proyecto).
- E2E verificado con broker CAÍDO: PUT aerolínea en flight-service → evento `UPDATE | ip truncada` en audit_log compartido y servido por `/api/audit-logs?action=UPDATE`. Jars reconstruidos y desplegados: flight/booking/mawb/warehouse.
- Nota: con broker arriba hay doble registro (audit_log + notification.audit_log) — inofensivo; la copia de notification queda cubierta por su propio retention job.

## Recent session changes (Aug 23, 2026 (15) — Mayor A resuelta: Retención de auditoría)
Cumple lo prometido en la Política de Privacidad (24 meses).
- **`IpAnonymizer`** (`common/util`): trunca último octeto IPv4 / últimos 4 grupos IPv6. Aplicado en `AuditEventStore.append()` y en el publisher AMQP común (`com.aircargo.common.audit.AuditService`) → TODOS los servicios pseudonimizan al escribir.
- **`AuditRetentionJob`**: auth-service diario 03:30 purga `audit_event` + `audit_log` legacy; notification-service diario 03:35 purga su copia (`notification.audit_log`) con `@EnableScheduling` añadido. Configurable: `app.audit.retention-months=24`.
- Verificación: login E2E → nuevo evento guarda `ip=127.0.0.0` (truncada) mientras históricos permanecen intactos; test de integración inserta evento de hace 25 meses y la purga lo elimina dejando los recientes. Tests 23 auth + 10 common.
- Nota H2 para tests: usar `DATEADD(MONTH, -25, now())`, no `INTERVAL '25 months'`.

## Recent session changes (Aug 23, 2026 (14) — Debilidad #6 resuelta: Política de privacidad + DPA)
- **`Documents/POLITICA-PRIVACIDAD.md`**: documento formal completo (responsable, datos tratados, finalidades, base legal Art.5 L172-13, conservación, seguridad implementada —BCrypt/AES/TLS/RBAC/MFA/backups—, derechos ARCO con habeas data, sin transferencias internacionales, cláusula de transparencia sobre desarrollo con IA). Campos `[CORCHETES]` por completar antes de publicar.
- **`Documents/CONTRATO-ENCARGO-TRATAMIENTO-DPA.md`**: plantilla DPA (objeto, instrucciones documentadas, confidencialidad, seguridad mínima, notificación de brechas 48h, subencargados con divulgación de herramientas IA y no-transmisión de datos personales, PI del código entregado, certificado de borrado al término).
- **In-app**: `PrivacyPolicyView.vue` pública en `/privacy` (bilingüe es/en vía locale, tablas de categorías de datos, lista de medidas reales), ruta en `publicPaths` del router, enlace "Política de Privacidad" bajo el formulario de login. Claves i18n `privacy.*`. Lint/build OK.
- Pendiente relacionado: completar corchetes con razón social/contacto real del cliente; la anonimización de auditoría (retención) sigue pendiente como debilidad separada.

## Recent session changes (Aug 23, 2026 (13) — Debilidad #5 resuelta: Revocación central de tokens)
**Antes**: "revocación" = Set en memoria del JwtUtil (por instancia, perdida en restart, invisible para los demás servicios); access token TTL **24h**; bloquear/desactivar usuario no mataba sus sesiones; refresh no chequeaba `blocked`.
**Ahora — revocación central vía `tokens_valid_from` (V21 ≡ raíz V47)**:
- `AppUser.tokensValidFrom` (TIMESTAMPTZ, null = sin restricción). Todo token (access o refresh) con `iat` anterior deja de ser válido.
- `TokenRevocationService.bump(userId)`: se invoca en **bloqueo**, resetPassword, enable/disable MFA, set-password-token, SetPassword y ChangePassword handlers. Lectura memoizada 30s (`TokenRevocationFilter` registrado tras JwtAuthFilter SOLO en auth-service → 401 `{"error":"Session revoked"}`).
- `/api/auth/refresh`: ahora valida además `blocked` y staleness del refresh token.
- `app.jwt.expiration-ms`: 24h → **1h** (ventana máxima de exposición residual en servicios stateless; el gateway sigue sin chequeo por-request al no tener BD).
- E2E verificado: login víctima → admin bloquea → /me 401 Session revoked ✓ → refresh 401 User blocked ✓. Tests 22+6 OK.
- Pendiente documentado: migrar token a httpOnly cookie (mitiga XSS de raíz); chequeo por-request en el resto de servicios requeriría Feign al auth o vista compartida.

## Recent session changes (Aug 23, 2026 (12) — Debilidad #4 resuelta: Flyway explícito en auth-service)
**Eliminado `ddl-auto=update`; esquema ahora 100% migraciones.**
- **`V20__baseline_auth_schema.sql`** (≡ raíz `V46__baseline_auth_schema.sql`): baseline idempotente con el esquema REAL de auth (9 tablas: airline, app_user, site, user_sites, view_permission, role_permission, audit_log, audit_event, password_reset_token) — `CREATE TABLE IF NOT EXISTS` + `ALTER ADD COLUMN IF NOT EXISTS` para columnas que deployments legacy puedan carecer + constraint de roles en DO block. Funciona igual en BD existente (no-ops) y BD nueva (esquema completo).
- V18/V19 endurecidos con guards de existencia de tabla (en BD vacía antes fallaban: ALTER sobre tabla inexistente / backfill desde audit_log inexistente). Seguro editarlos: Flyway jamás se había ejecutado para auth en ninguna BD.
- **Config**: `spring.flyway.enabled=true`, `table=flyway_schema_history_auth`, `baseline-on-migrate=true`, `baseline-version=0`, y **`ddl-auto=validate`** — cualquier drift entidad↔esquema ahora FALLA el arranque en vez de mutar silenciosamente.
- Verificado sobre la BD real: baseline v0 + V18+V19+V20 aplicadas (`flyway_schema_history_auth` con 4 filas success=true), boot en validate sin errores, login E2E OK.
- Regla nueva para devs: TODO cambio de esquema de auth = nueva migración `V21__...sql` (sincronizar a `database/migrations/V47__...`); prohibido tocar entidades esperando que Hibernate "arregle" la BD.

## Recent session changes (Aug 23, 2026 (11) — Debilidad #3 resuelta: Enlaces de reset de un solo uso)
**Eliminadas las contraseñas temporales compartidas.**
- **Antes**: admin generaba contraseña de 12 chars → el API la devolvía en la respuesta HTTP → se compartía por WhatsApp/chat y seguía válida si el usuario no la cambiaba.
- **Ahora**: `POST /api/users/{id}/generate-reset-link` (ADMIN/SUPER_USER) genera token de 32 bytes aleatorios; en BD solo se guarda su SHA-256 (`password_reset_token`, expira 15 min, un solo uso — los tokens pendientes anteriores del usuario se invalidan). Devuelve el ENLACE `{frontend}/set-password?token=...` una única vez. Nadie conoce la contraseña excepto el usuario que la escribe.
- Endpoints públicos nuevos (gateway `JwtGatewayFilter.PUBLIC_PATHS` + auth SecurityConfig): `POST /api/auth/reset-password/validate` (el frontend verifica el enlace antes de mostrar el form) y `POST /api/auth/set-password-token` (@StrongPassword aplicada; setea hash, limpia mustChangePassword/failedLoginAttempts/lockedUntil, marca usado, audita PASSWORD_SET, devuelve JWT).
- Frontend: `SetPasswordView` con modo `?token=` (valida al montar, estado "enlace inválido/expirado", sin email ni contraseña actual); `SettingsView` muestra el enlace copiable en el modal (i18n actualizado es/en); `usersApi.generateResetLink` reemplaza a generateTempPassword.
- SMTP notification-service: defaults ahora seguros y env-driven (`SMTP_AUTH:true`, `SMTP_STARTTLS:true`).
- Tests: 22 en auth (nuevos: token inválido→400, flujo completo débil→400/strong→200+JWT/reuso→400/login final OK). E2E real por gateway: los 5 pasos verificados ✓.
- Lecciones ops: (1) reconstruir jar SIEMPRE con el servicio detenido (jar corrupto = ClassNotFoundException al boot); (2) rutas públicas nuevas deben agregarse a `JwtGatewayFilter.PUBLIC_PATHS` del gateway además del SecurityConfig del servicio.

## Recent session changes (Aug 23, 2026 (10) — Debilidad #2 resuelta: Cifrado en reposo)
**AES-256-GCM transparente para datos sensibles.**
- **`aircargo-common/crypto/`**: `Crypto` (estático, formato `enc:v1:` + Base64(iv‖ciphertext), IV aleatorio por operación), `CryptoAttributeConverter` (JPA `@Converter`: cifra al escribir / descifra al leer — PDFs y lógica intactos), `CryptoConfig` (@Configuration inicializa desde `app.crypto.key`, Base64 32 bytes; sin key = passthrough con WARN).
- **Migración perezosa**: valores legacy en texto plano se leen tal cual (sin prefijo) y se cifran la próxima vez que se escriban.
- **Campos anotados**: `AppUser.mfaSecret` + `WarehouseReceipt`: deliveredBy/receivedBy/broker `{IdNum,IdDocUrl,SigUrl}` (10 campos: cédulas, fotos de ID, firmas) + dockSignature.
- **Key**: generada y agregada al `.env` real (`APP_ENCRYPTION_KEY`), placeholder en `.env.example`. En k8s agregar al Secret `aircargo-secrets` + env var en deployments.
- **Verificación E2E REAL**: MFA habilitado vía API → BD guarda `enc:v1:...` → login con TOTP devuelve token (backend descifró el secret para validarlo). CryptoTest 6/6 (roundtrip, IV único, legacy passthrough, key inválida). Reactor auth+warehouse+common: 30 tests OK.
- Lección ops: al reconstruir módulos dependientes, incluir `-pl aircargo-common -am` e `install` (un jar con common viejo falla boot con TypeNotPresentException); matar servicios ANTES de `mvn install` (jar corrupto si el proceso lo tiene abierto).

## Recent session changes (Aug 23, 2026 (9) — Debilidad #1 resuelta: Backups de BD)
**Respaldo automático diario + restore verificado.**
- `scripts/db-backup.sh`: `pg_dump -Fc` (custom comprimido, no detiene la BD) vía TCP como `aircargo_user` (sin sudo), retención configurable (`BACKUP_KEEP_DAYS`, default 30), destino `BACKUP_DIR` (default `~/aircargo-backups` — FUERA del repo).
- systemd **user timer**: `~/.config/systemd/user/{aircargo-backup.service,aircargo-backup.timer}` — diario 02:00, `Persistent=true` (recupera corridas perdidas). Estado: enabled. Limitación: los timers de usuario solo corren con sesión activa; para que corra siempre: `sudo loginctl enable-linger manolov`.
- **Restore PROBADO** (23 ago): dump → `pg_restore` a BD temporal → conteos idénticos en app_user(20), mawb(24), booking(24), uld(6), flight(3), warehouse_receipt(2). Comando de restore documentado:
  `pg_restore -h 127.0.0.1 -U aircargo_user -d <bd_nueva> ~/aircargo-backups/<archivo>.dump`
- Pendiente recomendado: copia OFFSITE (USB/nube) — un backup en el mismo disco que la BD no protege contra fallo de disco.

## Recent session changes (Aug 23, 2026 (8) — Fix meses vacíos en date picker)
El dropdown de mes salía vacío: **`t()` de vue-i18n sobre arrays los interpreta como candidatos de pluralización y NO devuelve la lista** (`t('common.months')` → string, no array; por eso tampoco se veía el mes seleccionado). Fix definitivo en `LocaleDatePicker.vue`: meses completos/abreviados y días de semana se generan con **`Intl.DateTimeFormat`** según el locale activo (`es-DO`/`en-US`) — i18n nativo del navegador sin depender de claves array. Las claves `common.months/monthsShort/weekdaysShort` quedan solo como referencia (no usarlas con `t()`; para arrays de i18n usar `tm()` y normalizar AST). Lint/build OK.

## Recent session changes (Aug 23, 2026 (7) — Date picker simplificado)
Rediseño completo de `LocaleDatePicker.vue` (usado en FilterBar, Flights, WarehouseReceipts y LoadPlanning) por feedback "no es claro": **header de una sola fila** `[‹] [select Mes] [select Año] [›]` — saltos directos con dropdowns nativos (mes completo i18n, rango año −10…+2) en vez de los confusos botones dobles «‹ ›»; celdas 32px con hoy resaltado en azul, fines de semana atenuados, seleccionado en negro; footer con botones sólidos **Hoy** (ancho completo) y **Limpiar** (solo si hay fecha); cierra al elegir o clic-fuera. Mismo contrato: `modelValue` ISO `yyyy-mm-dd` + evento `change`.

## Recent session changes (Aug 23, 2026 (6) — Scroll en SecurityView)
La vista cargaba pero no se podía scrollear: su raíz usaba `.ds-page`, que es `h-screen max-h-screen overflow-hidden flex-col` — con 3 secciones `ds-table-section` (`flex-1 min-h-0`) cada una recibía ~⅓ de viewport y el contenido restante quedaba atrapado sin scroll alcanzable. Fix: raíz de SecurityView cambiada al patrón de página de altura natural (`min-h-screen` + padding, mismo de UsersView) para que el `<main class="flex-1 overflow-auto">` de App.vue sea el contenedor de scroll único. La tabla de auditoría conserva su `max-height:400px` interno. Nota design system: `.ds-page` solo sirve para vistas que dimensionan TODAS sus secciones dentro del viewport.

## Recent session changes (Aug 23, 2026 (5) — Fix SecurityView en blanco)
La vista de seguridad no renderizaba nada: `Uncaught TypeError: _ctx.filteredAuditLogs is undefined` — la plantilla (filas del log de auditoría + estado vacío) usa `filteredAuditLogs` pero la computed **nunca fue declarada** en el script (regresión de la sesión "Security view en vivo": se añadió el dropdown `auditFilter` sin su lógica de filtrado). Añadida: `filteredAuditLogs = computed(() => auditFilter ? auditLogs.filter(l => l.action === auditFilter) : auditLogs)`. Lección: lint/build no detectan referencias template→script faltantes; el error solo aparece en consola del navegador en runtime.

## Recent session changes (Aug 23, 2026 (4) — Fix JDBC login sobre datadir histórico)
Al conectar el stack al Postgres del sistema (`/var/lib/postgres/data`, BD real `aircargo_db` con los 20 usuarios), el login daba "JDBC exception execution error": **`column au1_0.blocked does not exist`**. Causa raíz: `ddl-auto=update` genera `ALTER TABLE app_user ADD COLUMN blocked boolean NOT NULL` **sin DEFAULT** → PostgreSQL lo rechaza sobre tablas con filas ("contains null values") y Hibernate lo traga como warning silencioso; cualquier SELECT que incluya la columna falla después.
- **Fix inmediato**: `ALTER TABLE app_user ADD COLUMN IF NOT EXISTS blocked BOOLEAN NOT NULL DEFAULT false` (verificadas también `audit_event`, `uld_type_catalog`, `notification.audit_log` — todas presentes en el datadir histórico ✓).
- **Fix preventivo**: `AppUser.java` — los 6 booleanos/int NOT NULL (`blocked, mfa_enabled, mfa_locked, must_change_password, is_active, failed_login_attempts`) ahora llevan `columnDefinition = "... default false/true/0"` para que futuros ALTERs generados por hbm2ddl incluyan DEFAULT y no vuelvan a fallar sobre tablas pobladas.
Verificación: reinicio completo del stack → login vía gateway responde 401 genérico (sin excepción), 0 errores JDBC tras arranque, 20 usuarios intactos. Nota operativa: reconstruir un jar mientras su proceso está corriendo puede dejarlo corrupto (ClassNotFoundException en boot) — matar el servicio antes de `mvn install`.

## Recent session changes (Aug 23, 2026 (3) — Claves i18n faltantes)
Varias vistas mostraban la ruta cruda (`loadPlanning.lblGross`, etc.) porque usaban claves inexistentes. **Añadidas 25+ claves faltantes a es.js/en.js**: `loadPlanning.{positions,lblTotalUlds,lblGross,lblPayload,lblAvailable,dispatchFlight,dragFloatingHint,emptySelectFlight,emptyUld}`, `bookings.{importXlsx,exportCsv,saveChanges,create,selectFlightFirst}`, `bookings.form.{consigneeLabel,mawbNumber,reservedKg,units,priority,notesPlaceholder}`, `bookings.import.{previewTitle,cnee,unitsShort,commodityShort,importing}`, `common.refresh`, `settings.airlines.validation`, `settings.uldConfig.selectAirline`, `warehouse.evidence.{title,mawbEvidence}` (es las tenía incompletas; en ya traía evidence). **Guard de regresión**: script node ad-hoc que aplana es.js/en.js y cruza contra todos los `t('...')` literales de `src/views/*` — resultado actual: ✓ cero claves faltantes en ambos idiomas (falsos positivos conocidos: matches sobre `createElement('input'/'canvas')`). Nota: `loadPlanning.status.*` es dinámico con fallback `te()` — OK por diseño.

## Recent session changes (Aug 23, 2026 (2) — UX: % ULD y date picker)
- **`UldsView.vue`**: input de `% Volumen` ampliado w-14→w-20 y `piecesPct` w-10→w-14 (`inputmode=decimal`). Spinners nativos ocultos en TODOS los navegadores: el CSS scoped solo tenía la regla WebKit; añadida `input[type="number"] { -moz-appearance: textfield; appearance: textfield }` (Firefox mostraba flechas ↑↓ — por eso el usuario las veía). Ingreso queda 100% manual.
- **`LocaleDatePicker.vue` rediseñado** (más grande e intuitivo): panel 236→272px, header con **nombre completo del mes** ("Agosto 2026" / "August 2026") centrado y agrupado [«‹] título [›»], celdas h-6→h-8 texto 11→13px, weekdays con separador, footer muestra la fecha seleccionada junto al ✕ limpiar, botones de navegación más grandes (26px) con tooltips es/en. Nueva clave i18n `common.months` (12 meses completos es/en).

## Recent session changes (Aug 23, 2026 — Rutas de BD limpias + config lista para servidor)
- **Eliminados** los paths erróneos generados por el fallback: `proyecto/.local-pg/` y `~/.local-rabbitmq/`. La BD real del usuario siempre fue el Postgres del SISTEMA (`/var/lib/postgres/data`, datadir Arch con los datos históricos) — arrancar con `sudo systemctl enable --now postgresql`; si falla por "database files are incompatible" → `pg_upgrade`/`postgresql-old-upgrade` (pacman actualizó major).
- **Config de BD 100% por variables de entorno** (verificado: los 7 servicios con DB usan `${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:aircargo}`; gateway y load-planning no tienen DB por diseño). En servidor no hay que tocar código ni properties: solo variables.
- **Estructura de despliegue**: local = localhost; Docker Compose = contenedor `aircargo-db`; k8s = ConfigMap `POSTGRES_HOST: postgres` apuntando al Service interno + PVC persistente (`k8s/postgres.yml`) + Secret externo `aircargo-secrets` (USER/PASSWORD/JWT_SECRET). Para RDS/host externo basta poner `POSTGRES_HOST` en el ConfigMap.
- **Alineación de defaults** (eliminada trampa `aircargo_db` vs `aircargo`): `.env.example` ahora usa `POSTGRES_DB=aircargo`, compose default ídem, y documenta `POSTGRES_HOST` comentado.

## Recent session changes (Aug 22, 2026 (5) — start-all.sh arranca sin Docker)
`./start-all.sh` ahora degrada gracefully cuando el daemon de Docker está caído (estado actual de esta máquina):
- **Postgres NATIVO como fallback**: si :5432 no responde y Docker no está disponible, usa `initdb/pg_ctl` del sistema con datadir local `.local-pg/` (gitignored, se reutiliza entre arranques), auth trust en localhost, socket en `/tmp` (`unix_socket_directories=/tmp`, el default `/run/postgresql` no existe sin root), `max_connections=150` igual que compose, y crea la BD compartida `${POSTGRES_DB:-aircargo}` si falta (idempotente).
- **RabbitMQ NATIVO** con home propio (`RABBITMQ_BASE=~/.local-rabbitmq` + `CONF_ENV_FILE` para redirigir mnesia/logs fuera de `/var/lib/rabbitmq`) y alta de usuario `${RABBITMQ_USER}` idempotente vía rabbitmqctl. **En esta máquina el Rabbit nativo NO puede correr** (Erlang del sistema incompatible: boot falla con `horus/extraction_denied/unknown_instruction`) → tras ~40s sigue sin broker y notification-service arranca SIN listeners AMQP (comportamiento ya contemplado). Cuando Docker vuelva, tiene prioridad automáticamente.
- Build ahora offline (`mvn -o install -DskipTests`) para evitar stalls de red.
Verificación E2E real: Gateway UP en 25s; los 10 servicios escuchando (9092–9100 + 8080), Vite 5173 HTTP 200, login por gateway devuelve el mensaje genérico del handler CQRS ("Email y/o contraseña incorrectos") probando gateway→auth→Postgres-nativo. Nota: los checks ad-hoc con `/dev/tcp` desde zsh dan FALSOS NEGATIVOS (zsh no lo emula); usar `ss -tlnp` o curl.

## Recent session changes (Aug 22, 2026 (4) — Revisión privacidad y legal RD/EE.UU.)
Auditoría de datos personales + comparación legal en `Documents/REVISION-PRIVACIDAD-LEGAL.md` (análisis técnico, NO asesoría legal).
Hallazgos: sin política de privacidad (0 menciones); contraseña temporal viaja en claro por SMTP sin TLS; `mfaSecret` plano en BD; cédulas (`deliveredByIdNum/brokerIdNum`) y firmas manuscritas (`dockSignature` base64) sin protección especial; auditoría con PII (email/nombre/IP) retención indefinida; JWT_SECRET con default inseguro embebido. Positivos: BCrypt centralizado, lockout 5 intentos, msg genérico, MFA, secret.yml fuera del repo, sin terceros/tracking.
Legal: RD = Ley 172-13 (consentimiento Art. 5, información Art. 6, seguridad Arts. 14-15, ARCO ≤10 días, habeas data; autoridad no operativa aún, reforma en agenda). EE.UU. = sin ley federal; state laws (~20) fuera de umbral salvo CPRA employee/B2B en CA; FTC §5 exige seguridad razonable; leyes de brechas en los 50 estados. **IA**: ninguna ley obliga a revelar código escrito con IA (solo sistemas de IA en runtime); app no incrusta IA → fuera de alcance Colorado/Texas/Utah/CA AI acts. Riesgos dev-con-IA: confidencialidad de prompts (planes sin training), Graphify mantener --code-only, cláusula PI en contrato.

## Recent session changes (Aug 22, 2026 (3) — CQRS + Event Sourcing en auth-service)
Refactor de autenticación y auditoría solicitado por el usuario ("usa cqrs, event sourcing para auditorías, valida inputs, límite 5 intentos, hashing, msg genérico").

**CQRS**: la lógica que vivía inline en `AuthController` (568 líneas) se extrajo al lado command. El controller ahora es un adaptador HTTP delgado (switch sobre `LoginOutcome.Status` / `PasswordOutcome.Status`).
**Event sourcing**: nueva entidad append-only `audit_event` (`event/AuditEvent.java`, tabla con `event_type`+`payload`; sin update/delete). `AuditEventStore` es la única vía de escritura; `AuditService` se volvió fachada delegante → los ~28 puntos de llamada existentes quedaron event-sourced sin tocarlos. Acciones normalizadas: `CREATE→USER_CREATED`, `LOGIN→LOGIN_SUCCEEDED`, etc. **Lado query**: `query/AuditQueryService` sirve TODAS las lecturas desde eventos y hace merge con `audit_log` legacy (histórico previo sigue visible); `GET /api/audit-logs?entityType=` ahora SÍ filtra (bug: param ignorado).

| File | Change |
|------|--------|
| `command/LoginCommand(+Handler)`, `LoginOutcome` | **NEW** — login completo: checks estado, límite 5 intentos (`MAX_LOGIN_ATTEMPTS=5`) + lockout 30min, BCrypt match, MFA, JWT. Eventos: `LOGIN_FAILED` (con attemptCount/reason, también para UNKNOWN_USER sin revelar existencia), `ACCOUNT_LOCKED` (lockedUntil), `LOGIN_SUCCEEDED`. Email trim en command. Mensaje genérico único para email inexistente Y contraseña incorrecta: **"Email y/o contraseña incorrectos"** (401) |
| `command/SetPasswordCommand(+Handler)`, `ChangePasswordCommand(+Handler)`, `PasswordOutcome` | **NEW** — hashing BCrypt en handler, resetea contador al cambiar password, eventos `PASSWORD_SET`/`PASSWORD_CHANGED` |
| `event/{AuditEventType,AuditEvent,AuditEventRepository,AuditEventStore}.java` | **NEW** — store append-only + catálogo de tipos + lista SECURITY_ACTIONS |
| `service/AuditService.java` | Reescrita como fachada → delega en AuditEventStore; helpers nuevos `logLoginFailed/logAccountLocked`; acciones estandarizadas |
| `query/AuditQueryService.java` | **NEW** — lecturas CQRS: por usuario/acción/entidad/todos/security, merge evento+legacy ordenado por createdAt |
| `controller/{AuthController,AuditLogController}.java` | Adelgazados; audit-logs ahora 100% query-side |
| `config/PasswordEncoderConfig.java` | **NEW** — bean compartido `PasswordEncoder` (antes `new BCryptPasswordEncoder()` inline en 2 controllers; sin perfil → disponible en tests) |
| `AuthServiceApplication` | `@EnableScheduling` añadido (**fix**: el purge de sesiones de ActiveSessionTracker nunca corría) |
| `dto/AppUserDTO` | `@NotBlank @Email` en email (validación al crear/editar usuario; antes el @Valid no validaba nada) |
| `entity/AuditEvent` escaneo | `@EntityScan` ampliado con `authservice.event` |
| Migraciones | módulo `V19__create_audit_event.sql` ≡ raíz `V45__create_audit_event.sql` (DDL + backfill idempotente audit_log→audit_event con mapeo de nombres) |
| `frontend/src/views/SecurityView.vue` | Filtro `auditActions` y colores actualizados a nombres nuevos (LOGIN_SUCCEEDED azul, LOGIN_FAILED naranja, ACCOUNT_LOCKED rojo...) |

Validación requerida vs implementada: email ✓ (@Email en login/set-password/create-user), política de contraseña ✓ (@StrongPassword común: 12+ chars, mayús/minús/dígito/especial — ya existía), límite 5 ✓ (+eventos), hashing ✓ (BCrypt centralizado en bean), mensaje genérico ✓, autenticación ✓ (JWT intacto).
Tests: auth-service 14→**20** (nuevos: generic-msg igual para wrong-password y unknown-user, lockout tras 5 intentos con 6º rechazado incluso con password correcta, email inválido→400, password débil→400, eventos LOGIN_SUCCEEDED/FAILED servidos por el query side, create-user email inválido→400). Reactor completo BUILD SUCCESS (gateway 3 + auth 20 + flight 11 + booking 7 + mawb 11 + warehouse 4 + uld 11 = **67 tests**). Frontend lint/build OK. Contratos API intactos (mismos códigos HTTP y shapes; solo cambió el texto del error 401 de login).

## Recent session changes (Aug 22, 2026 (2) — Date picker con i18n)
**Problema**: el popup del `<input type="date">` nativo usa el idioma del NAVEGADOR (no de la app), por lo que mostraba meses en español aunque la app estuviera en inglés.
**Solución**: componente propio `frontend/src/components/LocaleDatePicker.vue` — calendario grid 6×7 totalmente i18n: navegación ±mes/±año, días de semana desde nueva clave `common.weekdaysShort` (Lu-Do / Mo-Su, lunes primero), meses de `common.monthsShort`, botón Hoy (`filterBar.periods.today`) y limpiar; formato mostrado según locale ("15 Ago 2026" / "Aug 15, 2026"); cierra al hacer click fuera; emite ISO `yyyy-mm-dd` vía `update:modelValue` + `change`.
**Reemplazos** (5 inputs nativos): FilterBar From/To (nuevos handlers `onDateFromValue/onDateToValue` que resetean `activePeriod='custom'`), FlightsView `form.flightDate`, WarehouseReceiptsView `filterDate` (sin @change: filtra por computada en cliente), LoadPlanningView `selectedDate` (@change="onDateChange" preservado).
**Verificación**: lint limpio, build OK.

## Recent session changes (Aug 22, 2026 — Security view en vivo + iconos de rol)

La vista de seguridad no reflejaba cambios al iniciar sesión con otro perfil (poll de 30s + heartbeat de 60s + sin feedback visual). Ahora es en vivo: poll de 10s (pausado si la pestaña está oculta; refresco inmediato al volver/recuperar foco), heartbeat global bajado a 30s, tiempos relativos que avanzan ("hace 12 s", tick cada 5s), indicador "En vivo · actualizado hace Xs" en el header, flash azul en las filas que cambian o son nuevas (diff por firma ignorando `lastHeartbeat` para sesiones), punto de estado real (verde pulsante si latido <2 min, ámbar si no) y filtro de eventos alineado con lo que realmente emite el backend (`LOGOUT`, `MFA_LOCKED`, `MFA_UNLOCKED`). Iconos de roles del sidebar mejorados y representativos del área.

| File | Change |
|------|--------|
| `frontend/src/views/SecurityView.vue` | **LIVE** — poll 30s→10s con pausa en `visibilitychange` + refresh en foco; reloj interno (`now`) cada 5s; `formatRel()` (ahora/hace Ns/min/h) con tooltip absoluto; `markFlash()` diff de sesiones (sin lastHeartbeat), audit logs (ids nuevos) y usuarios (fullName/role/email/blocked/isActive); clase `.row-flash` animada; estado de sesión dinámico (<2min verde pulse, sino ámbar); label `updatedAgo`; `toggleBlock` recarga datos tras bloquear |
| `frontend/src/App.vue` | Heartbeat global 60s→30s (presencia más fresca en `/audit-logs/connected`) |
| `frontend/src/i18n/es.js` + `en.js` | Nuevas claves `security.updatedAgo/justNow/secondsAgo/minutesAgo/hoursAgo` |
| `frontend/src/components/layout/Sidebar.vue` | Iconos de rol: SUPER_USER `IconCrownFilled`, ADMIN `IconShieldLock`, OPERATIONS `IconAirTrafficControl` (antes un camión), TRAFFIC `IconArrowsExchange`, LOAD_PLANNER `IconScale` (peso/balance), WAREHOUSE_ASSISTANT `IconForklift`, READ_ONLY `IconEye`; imports obsoletos (`IconShield/Crown/Tool/Truck/Clipboard`) removidos |

Notas: `npm run lint` y `npm run build` OK. Sin cambios de backend. Pendiente de aprobación: catálogo dinámico de tipos ULD según IATA (ver sección propuesta — el enum Java `UldType` es la única restricción; la columna DB ya es `VARCHAR(10)` sin CHECK).

## Recent session changes (Aug 22, 2026 — Catálogo dinámico de tipos ULD según IATA)

El tab "Config ULD" solo ofrecía los 11 tipos del enum Java `UldType` (PMC/PAH/PAG/PAJ/AAY/AAZ/AAD/PIP/BULK/AMP/AMJ) hardcodeados en el frontend y fijados por un enum en uld-service. Ahora los tipos viven en un catálogo dinámico en DB (`uld_type_catalog`, normas IATA): se siembra con los 11 tipos actuales + tipos estándar IATA adicionales (AKE, AKC, AKN, AKW, AMF, AQF, DPE, DQF, PLA, PLB, PAL, PNE, PQG, RKN, RMP, VKE, VRA, HME), ADMIN/SUPER_USER pueden registrar nuevos tipos desde Settings sin redeploy, y todo el sistema (formularios, escáner, config por aerolínea) lee del catálogo. El enum Java `UldType` fue ELIMINADO — `uld_type` es String validado por regex `^[A-Z0-9]{3,5}$` (la columna DB ya era `VARCHAR(10)` sin CHECK; `ddl-auto=validate` calza con la migración).

| File | Change |
|------|--------|
| `backend/aircargo-uld-service/src/main/resources/db/migration/V2__create_uld_type_catalog.sql` | **NEW** — tabla `uld_type_catalog` (code VARCHAR(5) UNIQUE, description VARCHAR(120), is_active, sort_order, timestamps) + seed idempotente (`ON CONFLICT DO NOTHING`) de 29 tipos: 11 legacy + estándares IATA (containers LD3/LD-11/AAU/plats/pallets racks/horses) |
| `database/migrations/V44__create_uld_type_catalog.sql` | Sincronizado desde uld-service |
| `backend/.../uldservice/entity/UldTypeCatalog.java` | **NEW** — entidad JPA |
| `backend/.../uldservice/repository/UldTypeCatalogRepository.java` | **NEW** — findAllByOrderBySortOrderAscCodeAsc, findByIsActiveTrue…, findByCodeIgnoreCase, existsByCodeIgnoreCase |
| `backend/.../uldservice/dto/UldTypeCatalogDTO.java` | **NEW** — DTO + fromEntity/toEntity |
| `backend/.../uldservice/service/UldTypeCatalogService(+Impl).java` | **NEW** — CRUD con validación (regex `^[A-Z0-9]{3,5}$`, duplicados), @Cacheable("uld-type-catalog") key 'active'/'all' + @CacheEvict(allEntries) en mutaciones |
| `backend/.../uldservice/controller/UldTypeCatalogController.java` | **NEW** — `/api/uld-type-catalog`: GET (?activeOnly=), GET/{id}, POST, PUT/{id}, DELETE/{id} |
| `backend/.../uldservice/util/UldTypes.java` | **NEW** — normalize() (trim+uppercase) e isValid() compartidos |
| `backend/.../uldservice/entity/UldType.java` | **DELETED** — enum eliminado; `entity/Uld.java` y `entity/UldTypeConfig.java` ahora usan String (setters normalizan); DTOs ídem |
| `backend/.../uldservice/{controller/UldController,service/UldServiceImpl,service/ScanService,service/PalletLabelService}.java` | Adaptados a String (quitado `UldType.valueOf()`/`.name()`); `UldServiceImpl.validateUldType()` valida en create/update; `UldTypeConfigServiceImpl` valida en create y bulk replaceForAirline. **FIX robustez** — `create()` ahora defaultea `status=OPEN` cuando el DTO no lo trae (la columna es NOT NULL y `toEntity` sobrescribía el default de la entidad con null → 500; la UI siempre lo enviaba pero cualquier consumidor directo del API explotaba) + test `create_defaultsStatusToOpen_whenStatusMissing` |
| `backend/.../uldservice/config/SecurityConfig.java` | GET `/api/uld-type-catalog/**` → todos los roles (incl. READ_ONLY); POST/PUT/DELETE → solo ADMIN/SUPER_USER |
| `backend/aircargo-gateway/.../config/RouteConfig.java` | Ruta uld-service ampliada con `/api/uld-type-catalog/**` |
| `frontend/src/api/uldTypeCatalog.js` | **NEW** — getAll/getById/create/update/remove |
| `frontend/src/views/SettingsView.vue` | Tab Config ULD: dropdown alimentado por catálogo (con descripción IATA en option/title), botón "+ Nuevo tipo ULD" con modal (código 3-5 chars + descripción) que crea vía API y recarga; **gestión completa del catálogo** en la misma pestaña: tabla con los 29 tipos (código, descripción, estado Activo/Inactivo toggle, editar descripción en el mismo modal con código fijo **preservando el estado activo actual**, eliminar con confirm), tipos inactivos desaparecen de formularios/escáner (`activeOnly=true`); incluye tipos fuera de catálogo ya configurados marcados "(fuera de catálogo)"; fallback a lista legacy si el catálogo no responde. **FIX latente**: `onMounted` asignaba `configAirlineId` programáticamente sin disparar el `@change` → las configs de la primera aerolínea nunca cargaban hasta cambiar el selector manualmente; ahora llama `loadTypeConfig()` tras asignar |
| `frontend/src/views/UldsView.vue` | Lista de tipos ahora ref cargada del catálogo (activos; fallback a lista legacy SOLO si el catálogo no responde — una respuesta exitosa se respeta tal cual para que los tipos desactivados no reaparezcan); autodetección de tipo al escanear usa el catálogo |
| `frontend/src/components/ScanPanel.vue` | Regex de detección de código ULD construida dinámicamente desde códigos del catálogo (fallback al patrón legacy solo si falla el fetch; respuesta exitosa se usa sin unión legacy) |
| `frontend/src/i18n/es.js` + `en.js` | Claves `settings.uldConfig.newType/newTypeTitle/newTypeHelp/newTypeDesc/newTypeDescPlaceholder/newTypeRequired/newTypeCreated/offCatalog/editTypeTitle` + subobjeto `catalog` (title/hint/state/active/inactive/deleteConfirm/updated/deleted) |
| `backend/.../uldservice/src/test/.../UldServiceImplTest.java` | FIX (preexistente): setUp pasaba 2 args al constructor (MawbClient añadido en sesión Aug 8) → NoSuchMethodError; ahora mockea MawbClient |

Verificación: `mvn -o test` reactor COMPLETO BUILD SUCCESS (auth 14 + flight 11 + booking 7 + mawb 11 + warehouse 4 + uld 10 + gateway 3 = 60 tests), `npm run lint`/`build` OK. **E2E real (Postgres nativo efímero :5433 + auth:9092 + uld:9097 + gateway:8080)**: Flyway aplicó V1+V2 y `ddl-auto=validate` pasó al arranque; login real vía gateway (`esantana@rannik.com`, DataSeeder) y con el token — GET `/api/uld-type-catalog` 29 tipos, POST `kma`→201 normalizado a `KMA`, duplicado→400, PUT→200, `?activeOnly=true` OK, DELETE→204, código inválido `K-M1`→400 (regex), sin token→403 (gateway y servicio). **Flujo integrador con tipo nuevo**: catálogo POST `TST`→201 → bulk config por aerolínea con `TST`→200 (antes imposible por enum) → GET config muestra TST → crear ULD `TST12345UP` sin status→201 default OPEN + netLbs 915 → persistido → tipo inválido en ULD→400. Detectado y corregido el 500 de status null en plena verificación. Nota: los códigos existentes en `uld_type_config`/`uld` siguen funcionando tal cual (String); el catálogo es aditivo. `stores/app.js inferUldType` y mapas de FlightDetail.vue quedan como fallback de inferencia local (no consultan catálogo).

## Recent session changes (Aug 14, 2026 — Eliminación de debilidades D1–D10 del análisis Bolt vs aircargo)

Trabajo dirigido por `~/Desktop/analisis-bolt-vs-aircargo.txt`. Objetivo: cerrar las debilidades reales detectadas. **Verificación final: `mvn test` reactor completo BUILD SUCCESS (63 tests: 7 unitarias + 1 integración auth + 10 de contrato feign) + `npm run lint`/`npm run build` OK.**

| File | Change |
|------|--------|
| `.github/workflows/ci.yml` | **D1 FIX** — eliminado `aircargo-api` del bucle "Build Docker images" (monolito borrado en Phase 12); CI estaba obsoleto |
| `aircargo-booking-service/.../dto/PageResponse.java` | **D2 DELETED** — código muerto: booking ya importaba `com.aircargo.common.dto.PageResponse` |
| `aircargo-uld-service/.../dto/PageResponse.java` | **D2 DELETED** — igual; UldService/UldServiceImpl/UldController ahora usan `com.aircargo.common.dto.PageResponse` |
| `aircargo-common/pom.xml` | **D3** — añadido `spring-boot-starter-amqp` `<optional>true</optional>` (común disponible solo si el servicio tiene amqp) |
| `aircargo-common/.../event/AuditLogEvent.java` | **D3 NEW** — record compartido de auditoría |
| `aircargo-common/.../audit/AuditService.java` | **D3 NEW** — publicador AMQP no bloqueante (routing key `audit.log`); `@Service("amqpAuditService")` para no colisionar con el `auditService` local de auth; `@ConditionalOnClass(RabbitTemplate)` → solo se registra donde hay amqp (booking, mawb, warehouse, flight, notification) |
| `aircargo-{booking,flight,mawb,warehouse}-service/.../service/AuditService.java` | **D3 DELETED** — 4 copias locales (2 estrategias distintas) eliminadas; los controllers ahora inyectan `com.aircargo.common.audit.AuditService` |
| `aircargo-{booking,mawb,warehouse}-service/.../event/AuditLogEvent.java` | **D3 DELETED** — 3 records locales duplicados eliminados |
| `aircargo-common/.../error/GlobalExceptionHandler.java` | **D4 NEW** — `@RestControllerAdvice` compartido + `@ConditionalOnWebApplication(SERVLET)` (no aplica al gateway reactivo); 400 validación/IllegalArgument, 404 NoResourceFound, re-lanza AccessDenied/Authentication, 500 genérico JSON consistente. Antes cada servicio devolvía JSON de error distinto |
| `aircargo-common/.../auth/JwtUtil.java` | **D8** — constructor con 3er parámetro `@Value("${app.jwt.allow-dev-secret:false}")`; fail-fast: lanza en startup si el secret es el dev default salvo `app.jwt.allow-dev-secret=true` (antes solo warn; un secret dev en producción era silencioso) |
| `aircargo-notification-service/.../db/migration/V2__create_audit_log.sql` | **D3 NEW** — tabla `notification.audit_log` + índices (schema `notification`, evita colisión con `public.audit_log` de auth) |
| `aircargo-notification-service/.../entity/AuditLog.java` | **D3 NEW** — entidad JPA `@Table(schema="notification")` |
| `aircargo-notification-service/.../repository/AuditLogRepository.java` | **D3 NEW** — repositorio |
| `aircargo-notification-service/.../config/RabbitConfig.java` | **D3** — binding `audit.log` → cola `audit.log`; **ANTES la routing key `audit.log` no tenía consumidor (los eventos de auditoría se perdían)** |
| `aircargo-notification-service/.../listener/NotificationEventListener.java` | **D3** — nuevo `@RabbitHandler` `onAuditLog` que persiste `AuditLog` con try/catch |
| `database/migrations/V43__create_notification_audit_log.sql` | Sincronizado desde notification-service |
| `aircargo-auth-service/.../entity/AppUser.java` | **FIX** — `update()` de `AppUserServiceImpl` sobrescribía email/fullName/role con `null` en DTOs parciales; ahora guards null-safe (bug detectado por `AppUserServiceImplTest`) |
| `aircargo-auth-service/src/test/resources/application-test.properties` | **D6 NEW** — H2, `ddl-auto=create-drop`, Flyway off, `app.jwt.allow-dev-secret=true`, cache simple |
| `aircargo-auth-service/.../config/TestSecurityConfig.java` | **D6 NEW** — `@Profile("test")` permitAll (antes AGENTS.md lo describía pero no existía) |
| `aircargo-auth-service/.../controller/AuthControllerIntegrationTest.java` | **D6 NEW** — 4 tests: login válido→200+token, usuario inexistente→401, inactivo→403, password faltante→428. Persiste `Airline` vía `EntityManager` (airline_id NOT NULL) |
| `AGENTS.md` | **D9 FIX** — sección "Hard-coded constants": el frontend ya NO hardcodea el UUID de UPS (carga desde `/api/airlines`); el seed vive en flight-service `V1__init.sql` (comentario del seed corregido) |
| `aircargo-common/.../cache/CacheConfig.java` | **D5 NEW** — `@Configuration` compartido con `@EnableCaching` + `@ConditionalOnClass(name="org.springframework.cache.caffeine.CaffeineCacheManager")` (el gateway reactivo no lo registra); lee `spring.cache.caffeine.spec` (antes muerta: los CacheConfig la ignoraban). Con `spring.cache.type=none` se DESACTIVA toda la caché — patrón documentado para HA sin Redis |
| Los 9 `aircargo-*-service/.../config/CacheConfig.java` | **D5 DELETED** — clases duplicadas eliminadas; la caché ahora la gestiona common. Cada servicio conserva su spec en `application.properties` (export 60s, load-planning 120s, auth 600s, resto 300s) |
| `aircargo-auth-service/.../AuthServiceApplication.java` | **D5** — añadido `@EnableCaching` (auth lo tenía solo en su CacheConfig borrado) |
| `aircargo-gateway/.../filter/RateLimitFilter.java` | **D7** — límites configurables: `app.gateway.rate-limit.enabled` / `.limit-per-minute` / `.timeout-ms` (defaults: true / 100 / 50). Sigue siendo en-memoria por instancia |
| `aircargo-gateway/.../resources/application.properties` | **D7** — sección Rate Limiting con env vars `RATE_LIMIT_ENABLED`/`RATE_LIMIT_PER_MINUTE`/`RATE_LIMIT_TIMEOUT_MS` + comentario HA (nginx `limit_req` o Redis en 2+ réplicas) |
| `aircargo-common/.../dto/LabelPrintRequest.java` | **D10 NEW** — request de impresión compartido (antes 2 copias byte-idénticas en mawb y uld) |
| `aircargo-{mawb,uld}-service/.../dto/LabelPrintRequest.java` | **D10 DELETED** — MawbLabelService/MawbLabelController/PalletLabelService/UldLabelController importan `com.aircargo.common.dto.LabelPrintRequest` |
| `aircargo-feign-clients/.../dto/UserDTO.java` | **D10** — contrato alineado con el wire real: `sites` → `siteIds` (`List<UUID>`) (auth responde `siteIds`; notification solo usa id/email) |
| 5 × `.../dto/FeignContractSyncTest.java` | **D10 NEW** — guarda de contrato por servicio productor (auth, flight, booking, mawb, uld): refleja cada DTO feign y falla el build si un campo del contrato falta en el DTO local (el local puede tener extras y tipos enum/String). Hace explícito el "patrón de contrato" de la Fase 8 |
| `aircargo-{auth,flight}-service/pom.xml` | **D10** — `aircargo-feign-clients` como dep `<scope>test</scope>` (solo para FeignContractSyncTest) |

Notas: `AppUser.airline` es `@JoinColumn(nullable=false)` → todo test de integración de login debe persistir una `Airline` primero. El `GlobalExceptionHandler` de common ya se verificó activo en el test de integración (errores 500 JSON consistentes). D5/D7 son infraestructura: la caché Caffeine en proceso y el rate limiter por instancia son CORRECTOS para 1 instancia por servicio (el diseño actual); para HA (2+ réplicas) se documenta: `spring.cache.type=none` (D5) y nginx/Redis (D7). Los TTLs de caché por servicio ahora viven en `spring.cache.caffeine.spec` de cada `application.properties` (live, antes muerta).

## Recent session changes (Aug 9, 2026 — Fix número HAWB en PDF recibo completo)

**FIX**: el PDF del recibo completo (`GET /api/warehouse/receipts/{id}/pdf` → `ReceiptFullPdfService`) mostraba `HAWB 1 de 1` / `HAWB 1` en el "Resumen General" y "Anexo — Desglose por HAWB" en vez del número real (ej. `4010096112`), porque agrupaba piezas por `hawbId` y usaba solo el índice del grupo sin resolver el número vía Feign. Ahora resuelve los números igual que `buildHawbBreakdownHtml` (lote por `getHawbsByMawb` + resolución individual `getHawbById`).

| File | Change |
|------|--------|
| `backend/.../warehouseservice/service/ReceiptFullPdfService.java` | **FIX** — inyectado `MawbClient`; nuevo helper `resolveHawbNumbers(receipt, byHawb)` (mismo patrón de fallback que `WarehouseServiceImpl.buildHawbBreakdownHtml`); `renderSummaryTable` y el anexo muestran `HAWB <número>` real en vez del índice; `renderHawbSummary` incluye `HAWB: <número>` |

Notas: verificado E2E vía gateway — PDF del recibo `56cb4b69` (MAWB `406-05912970`, HAWB `4010096112`) regenerado tras borrar `pdf_data` muestra `4010096112` en resumen, anexo y resumen de HAWB. `mvn -o test -pl aircargo-warehouse-service -am` pasa. El PDF se sirve desde `pdfData` persistido; para regenerar un recibo existente basta borrar su `pdf_data` en DB o re-emitir (los recibos sin HAWB vinculada —`hawbId` NULL en piezas— siguen sin sección de desglose por no existir HAWB que resolver).

# Recent session changes (Aug 9, 2026 — Fix 401 PDF evidencias + desglose HAWBs)

Fix del botón "descargar PDF de evidencias de recibo": devolvía 401 en recibo ya emitido. Causa raíz: `SAXParseException: The entity "middot" was referenced, but not declared` en openhtmltopdf → Spring Security enmascaraba la excepción como 401. Además se añadió desglose de HAWBs a las evidencias.

| File | Change |
|------|--------|
| `backend/.../warehouseservice/service/WarehouseServiceImpl.java` | **FIX 401** — `&mdash;`→`&#8212;`, `&middot;`→`&#183;` en los generadores de evidencias; `xmlEscape()` para valores dinámicos (name/mawbNum); **NEW** — `buildHawbBreakdownHtml()`: agrupa `ReceiptPiece` por `hawbId` y renderiza tabla (HAWB, consignatario, dest, pzas, dims, balanza/volum/cobrable lbs con subtotales por HAWB y TOTAL) en `getSupportingDocsHtml` y `getSupportingDocsPdf`; helpers `nz`/`fmt`/`dimPart` |
| `backend/.../warehouseservice/service/ReceiptFullPdfService.java` | `&mdash;`→`&#8212;` (mismo fallo openhtmltopdf en PDF completo) |
| `backend/aircargo-common/pom.xml` | Añadido `io.github.openfeign:feign-core` (para RequestInterceptor) |
| `backend/.../common/feign/FeignServiceAuthInterceptor.java` | **NEW** — `RequestInterceptor` global: propaga el `Authorization` del request entrante (RequestContextHolder) o, si no hay contexto (async/eventos), firma un token de servicio (`SUPER_USER`) con el `JwtUtil`/JWT_SECRET compartido. Sin esto el Feign service-to-service devolvía 403 (p.ej. `getHawbsByMawb`, `updateBookingAwb`, scan lookups) porque los servicios destino exigen auth |
| `backend/aircargo-feign-clients/.../client/MawbClient.java` | Añadido `GET /api/hawbs/mawb/{mawbId}` → `List<HawbDTO>` y `GET /api/hawbs/{id}` → `HawbDTO` |
| `backend/aircargo-feign-clients/.../dto/HawbDTO.java` | **NEW** — DTO Feign (id, mawbId, airlineId, hawbNumber, consigneeName, destination, pieces, weightKg, commodityType/status/notes como String) |

Notas: el desglose solo se renderiza cuando las piezas del recibo tienen `hawbId` (el frontend ya envía `hawbId` en `WarehouseReceiptsView.vue` líneas 1031/1407/1485). Resolución de HAWBs: primero lote por `mawbId` y, si falta alguna, resolución individual por `id` (evita mostrar el UUID corto siempre que la HAWB exista). Consignatario/destino con fallback al del recibo/MAWB cuando la HAWB no los tiene (caso HAWB única → queda el consignatario de la MAWB). Verificado E2E vía gateway: HAWB única `3961805399`/MEM → muestra número real + consignatario MAWB; 2 HAWBs → cada una con su número/consignatario/destino propios; PDF 200. Datos de prueba limpiados. `mvn -o compile` full reactor y `mvn -o test -pl aircargo-warehouse-service -am` pasan.

## Recent session changes (Aug 9, 2026 — Airlines CRUD + ULD type config + editable tare)

Feature: registrar más aerolíneas (solo ADMIN/SUPER_USER), configurar tipos de ULD por aerolínea, y tara editable en el formulario de ULD (antes 140 lbs fijas).

| File | Change |
|------|--------|
| `backend/.../flightservice/config/SecurityConfig.java` | POST/PUT/DELETE `/api/airlines/**` → solo ADMIN/SUPER_USER (GET público autenticado) |
| `backend/.../flightservice/controller/AirlineController.java` | Añadida auditoría (AuditService) en create/update/delete con patrón de FlightController |
| `backend/.../uldservice/dto/UldTypeConfigDTO.java` | **NEW** — DTO con fromEntity/toEntity |
| `backend/.../uldservice/service/UldTypeConfigService.java` + Impl | **NEW** — CRUD + `replaceAllForAirline` (bulk upsert: borra y recrea por aerolínea); valida airlineId/uldType; `@Cacheable("uld-type-config")` por airlineId/'all' + `@CacheEvict(allEntries)` en mutaciones |
| `backend/.../uldservice/entity/UldTypeConfig.java` | `@CreationTimestamp`/`@UpdateTimestamp` en createdAt/updatedAt (antes sin anotar; DB NOT NULL) |
| `backend/.../uldservice/repository/UldTypeConfigRepository.java` | Añadido `deleteByAirlineId(UUID)` |
| `backend/.../uldservice/controller/UldTypeConfigController.java` | **Ampliado** — GET (filtro opcional), GET /{airlineId}, GET /config/{id}, POST, PUT /{id}, DELETE /{id}, PUT /airline/{airlineId}/bulk |
| `backend/.../uldservice/config/CacheConfig.java` | Caché `uld-type-config` (Caffeine, TTL 10 min) |
| `backend/.../uldservice/config/SecurityConfig.java` | POST/PUT/DELETE `/api/uld-type-config/**` → solo ADMIN/SUPER_USER; GET sigue permitido a todos los roles |
| `frontend/src/api/airlines.js` | Añadidos create/update/delete |
| `frontend/src/api/uldTypeConfig.js` | **Ampliado** — getAll/getById/create/update/delete/replaceForAirline |
| `frontend/src/router/index.js` | ADMIN ahora puede ver SETTINGS (antes `view !== 'SETTINGS'`) |
| `frontend/src/stores/auth.js` | `canView('SETTINGS')` para ADMIN → true (mismo criterio que router) |
| `frontend/src/views/SettingsView.vue` | **NEW tabs** "Aerolíneas" y "Config ULD" (visibles para ADMIN/SUPER_USER): CRUD de aerolíneas con modales, tabla editable de config ULD por aerolínea (tipo, tara default, max gross, notas) con guardado bulk |
| `frontend/src/views/UldsView.vue` | **FIX** — `createNewBlankUld()` usa `defaultTareFor('PMC')` (config backend → TARE_MAP estático → 0) en vez de `tareLbs: 140` fija; `suggestedTareLbs` y auto-aplicación de tara al cambiar tipo de ULD en ULD sin guardar usan la config; `loadTypeConfig()` carga config por aerolínea del vuelo seleccionado (watch + onMounted) |

Notas: `mvn -o` (reactor completo) compila; `npm run lint` y `npm run build` pasan. Verificado E2E vía gateway con token real: CRUD aerolíneas (201/200/204), CRUD config ULD (201/bulk 200/DELETE), 403 para role OPERATIONS en mutaciones de ambos endpoints y 200 en GET. La tara por tipo/aerolínea se puede editar en Settings → Config ULD; el formulario de ULD sigue siendo editable manualmente (input `tareLbs` con botón "usar" de la sugerida).

## Recent session changes (Aug 8, 2026 — Bugfix audit: Feign integration + weight math)

Revisión completa de bugs (documento: `~/Desktop/Mejoras_a_aircargo-saas.txt`). Los clientes Feign apuntaban a rutas del monólito (`/api/cargo/...`) → 404 en el scan de ULDs, import de ramp manifest y cierre de vuelo.

| File | Change |
|------|--------|
| `backend/.../feign/client/MawbClient.java` | **FIX B1** — rutas `/api/cargo/mawbs/**` → `/api/mawbs/**` (el controller es `/api/mawbs`; el gateway reescribe solo para el navegador) |
| `backend/.../mawbservice/controller/MawbController.java` | **FIX B1** — nuevo `GET /awb/{awbNumber}` (usa `getByAwbNumber` ya existente en service) |
| `backend/.../feign/client/BookingClient.java` | **FIX B2** — `updateBookingAwb` ahora envía `Map<String,String>` (`{"awbNumber":...}`) acorde al DTO del server |
| `backend/.../bookingservice/controller/BookingController.java` | **FIX B2** — nuevos `GET /mawb/{mawbId}` y `GET /flight/{flightId}` |
| `backend/.../bookingservice/service/BookingService.java` + Impl | **FIX B2** — `findByMawbId`, `getByFlightId` (repo ya los soportaba) |
| `backend/.../feign/client/FlightClient.java` | **FIX B3** — `getAllFlights` → `GET /api/flights/list` (el GET base siempre devuelve PageResponse) |
| `backend/.../flightservice/controller/FlightController.java` | **FIX B3** — nuevo `GET /list` no paginado |
| `backend/.../warehouseservice/service/WarehouseServiceImpl.java` | **FIX F1** — `calculatePieceWeights` multiplica volumen por `pieces` (alinea con el frontend); **FIX B6** — `syncMawbAndBooking` ahora actualiza el AWB del booking; publica `com.aircargo.common.event.ReceiptCreatedEvent` (antes clase local con campo `awbNumber` distinto) |
| `frontend/src/views/WarehouseReceiptsView.vue` | **FIX F2** — `totalChargeableKg/Lbs` ahora suma `chargeable` por pieza (criterio IATA) en vez de max(Σdims, Σscales) |
| `frontend/src/views/LoadPlanningView.vue` | **FIX F3** — no envía `airlineId: ''` (causaba 400); omite el param si no hay vuelo |
| `frontend/src/views/RampUploadView.vue` | **FIX F4** — `flightId`/`airlineId` desde `route.query`; botón deshabilitado si faltan (antes UUIDs fake hardcodeados) |
| `backend/aircargo-common/.../event/` | **NEW** — records `BookingAwbUpdatedEvent`, `FlightDepartedEvent`, `MawbStatusChangedEvent` |
| `backend/aircargo-notification-service/.../listener/` | **FIX B4** — 3 listeners (`Receipt/Booking/FlightEventListener`) en la MISMA cola → único `NotificationEventListener` con un `@RabbitHandler` por tipo |
| `backend/.../notificationservice/config/RabbitConfig.java` | **FIX B4** — binding `booking.confirmed` → `booking.awb.updated` (key real publicada) |
| `backend/.../mawbservice/service/MawbServiceImpl.java` | **FIX B8** — `create` ya no fuerza `BOOKED` si el DTO trae status; **NEW** — publica `mawb.status.changed` (RabbitTemplate) |
| `backend/.../bookingservice/service/BookingServiceImpl.java` | Publica `com.aircargo.common.event.BookingAwbUpdatedEvent` (se elimina la clase local duplicada) |
| `backend/aircargo-common/.../auth/JwtUtil.java` | **FIX B7** — `generateAccessToken` usa `expirationMs` configurable (antes constante 15 min muerta) |
| `frontend/src/stores/ulds.js` | **FIX F7** — `loadUldsForFlight` desenvuelve `res.data.content \|\| res.data` |
| `frontend/src/components/ScanPanel.vue` | **FIX F10** — clases Tailwind dinámicas → mapa estático `FLASH_CLASSES` (el JIT no genera `border-${c}-400`) |
| `backend/.../authservice/service/AppUserServiceImpl.java` | **FIX B16** — `@Cacheable("users")` key `#airlineId` → `#airlineId != null ? #airlineId : 'all'` (clave nula → `Null key returned for cache operation` → 500 en `GET /api/users` sin query param; encontrado en verificación E2E) |
| `backend/.../mawbservice/service/HawbServiceImpl.java` | **FIX B17** — `create` deriva `airlineId` del MAWB padre (`mawbRepository.findById`) cuando el DTO no lo trae; si sigue null → `IllegalArgumentException` claro en vez de 500. `hawb.airline_id` es NOT NULL y el flujo de recibos (`WarehouseReceiptsView`) crea HAWBs sin `airlineId` → `DataIntegrityViolationException`. También ya no fuerza `status=BOOKED` (respeta el del DTO, alineado con B8) |

Notas: `mvn -o compile` (reactor completo) y `mvn -o test` (módulos tocados) pasan; `npm run lint` y `npm run build` pasan. Pendiente B5 (revocación JWT centralizada) y publisher de `flight.departed` (load-planning no tiene dep amqp). Detalle completo en el documento del escritorio.

**Verificación E2E (stack local):** los 10 servicios arrancan y todos los endpoints clave responden vía gateway con token real (`/api/flights/list` 200, `/api/mawbs` 200, `/api/ulds` 200, `/api/bookings` 200, `/api/receipts` 200, `/api/users` 200, `/api/users/connected` 200, `/api/sites` 200, `/api/audit-logs` 200, `/api/label-templates` 200, `/api/bi/dashboard` 200, `GET /api/mawbs/awb/{n}` 200, `GET /api/bookings/mawb/{id}` 200, `GET /api/bookings/flight/{id}` 200). Nota operativa: al arrancar los servicios manualmente con `java -jar` hay que cargar `.env` (`set -a; . ./aircargo-env.sh; set +a`) — si el gateway arranca sin `JWT_SECRET` valida con el secret dev y rechaza los tokens emitidos con el secret real (401 "Invalid or expired token").

## Recent session changes (Aug 6, 2026 — Label Printing: Cargo Labels + Pallet Labels)

Sistema de impresión de etiquetas tipo Zebra Designer: plantillas configurables (solo SUPER_USER) para etiquetas de carga por MAWB y pallet labels por ULD; impresión ZPL (Zebra) y PDF para todos los usuarios; tamaños 2x1, 3x2, 4x3, 4x6, 6x4 pulgadas con orientación horizontal/vertical; códigos CODE128 + QR (zxing). Modelos de referencia del usuario en `~/Desktop/Projects/Reference-Labels/` (NiceLabel `.nlbl` cifrados, solo nombres legibles).

| File | Change |
|------|--------|
| `backend/.../mawbservice/resources/db/migration/V3__create_label_template.sql` | **NEW** — tabla `label_template` (name, type CARGO/PALLET, width/height_inches, orientation, dpi, config_json, is_default, timestamps) + índice por tipo |
| `database/migrations/V42__create_label_template.sql` | Sincronizado desde mawb-service |
| `backend/.../mawbservice/entity/LabelTemplate.java` | **NEW** — entidad JPA con `@Enumerated` LabelType y `orientation` |
| `backend/.../mawbservice/entity/LabelType.java` | **NEW** — enum `CARGO`, `PALLET` |
| `backend/.../mawbservice/dto/LabelTemplateDTO.java` | **NEW** — DTO con `fromEntity`/`toEntity` |
| `backend/.../mawbservice/dto/LabelPrintRequest.java` | **NEW** — `templateId`, `format` (PDF/ZPL), `ids`, `quantity`, `overrides` |
| `backend/.../mawbservice/repository/LabelTemplateRepository.java` | **NEW** — findByType, findByTypeAndIsDefaultTrue |
| `backend/.../mawbservice/service/LabelTemplateService.java` | **NEW** — CRUD + gestión de default por tipo |
| `backend/.../mawbservice/controller/LabelTemplateController.java` | **NEW** — `GET/POST /api/label-templates`, `PUT/DELETE /api/label-templates/{id}` + auditoría |
| `backend/.../mawbservice/service/MawbLabelService.java` | **NEW** — resuelve plantilla (id o default CARGO), datos por MAWB (AWB_NUMBER, SHIPPER_NAME, CONSIGNEE_NAME, ORIGIN, DESTINATION, PIECES, WEIGHT_KG, CHARGEABLE_KG, COMMODITY, STATUS) |
| `backend/.../mawbservice/controller/MawbLabelController.java` | **NEW** — `POST /api/mawbs/labels` → PDF `.pdf` o ZPL `.zpl` (attachment), errores JSON |
| `backend/.../mawbservice/config/SecurityConfig.java` | POST/PUT/DELETE `/api/label-templates/**` → solo SUPER_USER |
| `backend/aircargo-common/.../label/LabelRenderer.java` | **NEW** — renderer compartido (`@Component`): ZPL (`^PW/^LL/^FO/^A0N/^FB/^BC/^BQ/^GB` + escape) y PDF (openhtmltopdf, `@page` por tamaño, barcode/QR PNG base64); `LabelSpec` con `orientation` (V/H swap) |
| `backend/aircargo-common/pom.xml` | Añadidos `com.google.zxing:core/javase:3.5.3` y `com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10` |
| `backend/aircargo-feign-clients/.../dto/LabelTemplateDTO.java` | **NEW** — DTO Feign (type como String, incluye orientation) |
| `backend/aircargo-feign-clients/.../client/MawbClient.java` | Añadidos `GET /api/label-templates?type=` y `GET /api/label-templates/{id}` |
| `backend/.../uldservice/dto/LabelPrintRequest.java` | **NEW** — mismo DTO que mawb |
| `backend/.../uldservice/service/PalletLabelService.java` | **NEW** — resuelve plantilla PALLET vía MawbClient, datos por ULD (ULD_NUMBER, ULD_TYPE, POSITION, CONFIG, SEAL, STATUS, GROSS/TARE/NET lbs+kg, PIECES sumado, MAWBS_COUNT) |
| `backend/.../uldservice/controller/UldLabelController.java` | **NEW** — `POST /api/ulds/labels` → PDF/ZPL |
| `backend/.../uldservice/config/SecurityConfig.java` | `/api/ulds/labels/**` → OPERATIONS/TRAFFIC/LOAD_PLANNER/ADMIN/SUPER_USER |
| `backend/.../gateway/config/RouteConfig.java` | Ruta mawb-service ampliada con `/api/label-templates/**` |
| `frontend/src/api/labelTemplates.js` | **NEW** — CRUD plantillas + `labelsApi.downloadCargo/downloadPallet` (blob, lee Content-Disposition) |
| `frontend/src/utils/labelConfig.js` | **NEW** — SIZE_PRESETS, FIELDS y SAMPLE_DATA por tipo, `effectiveSize` (orientation swap), `defaultElement` |
| `frontend/src/components/labels/LabelDesignerModal.vue` | **NEW** — editor tipo Zebra Designer (canvas escalado, arrastrar/redimensionar, cuadrícula mm, toolbox texto/CODE128/QR/línea/rect, propiedades por elemento, tamaño/orientación/DPI, default) — solo visible para SUPER_USER |
| `frontend/src/components/labels/LabelPrintModal.vue` | **NEW** — selector de plantilla + formato PDF/ZPL + copias + vista previa + descarga (todos los usuarios); botón editar plantillas si SUPER_USER |
| `frontend/src/views/MawbsView.vue` | Botón "Etiquetas" en header → imprime cargo labels de los MAWBs visibles |
| `frontend/src/views/UldsView.vue` | Botón "Pallet Label" en el ULD expandido (requiere ULD guardado) |

Notas: el renderer común necesita `ObjectMapper` (auto-configurado por Spring Boot en todos los servicios). El preview del diseñador muestra placeholders de barcode/QR; el código real se genera en ZPL/PDF (zxing). Endpoints de etiquetas funcionan a través del gateway (`/api/mawbs/labels`, `/api/ulds/labels`, `/api/label-templates/**`).

## Recent session changes (Aug 6, 2026 — Connection Pooling tuning + Cache layer completion)

Evaluación técnica en `~/Desktop/revision1.txt` (connection pooling · capa de caché · CDN).

| File | Change |
|------|--------|
| `backend/.../authservice/config/CacheConfig.java` | **NEW** — `@EnableCaching` + Caffeine CacheManager (caches `users`, `sites`, TTL 10 min). La caché del auth-service estaba DESACTIVADA (deps presentes sin `@EnableCaching`). |
| `backend/.../authservice/service/AppUserServiceImpl.java` | `@Cacheable("users")` en getAll/getById; `@CacheEvict` en create/update/delete/resetPassword |
| `backend/.../authservice/service/SiteService.java` | `@Cacheable("sites")` en getAll/getActive/getById; `@CacheEvict` en create/update/delete |
| `backend/.../authservice/resources/application.properties` | Añadido `spring.cache.type=caffeine` + `spring.cache.caffeine.spec` (antes no existía) |
| `backend/.../exportservice/config/CacheConfig.java` | Ahora registra caché `bi` (Caffeine, 500 entradas, TTL **60s**) — antes @EnableCaching sin cache names ni uso |
| `backend/.../exportservice/service/BiService.java` | `@Cacheable("bi")` en los 12 agregados (getFlights, getBookings, getMawbs, getReceipts, getUlds, getDashboard, getDaily, getSummary, getByLocation, getTimeline, getTopMawbs, getFlightPerformance) con keys por método/params/fechas. Antes: caché inerte (ningún @Cacheable). |
| `backend/.../uldservice/service/UldServiceImpl.java` | `@Cacheable("ulds")` en getAll/getById; `@CacheEvict({"ulds","uld-awbs"})` en create/update/transferUld/assignFlight/delete. Antes: @EnableCaching sin @Cacheable. |
| `backend/.../uldservice/service/UldAwbServiceImpl.java` | `@Cacheable("uld-awbs")` en getAll/getById; `@CacheEvict({"uld-awbs","ulds"})` en create/update/delete |
| `backend/.../uldservice/service/ScanService.java` | `@CacheEvict({"uld-awbs","ulds"})` en registerPiece/undoLastPiece (el scan modifica piezas/ULD-AWB) |
| Todos los `application.properties` con DB (auth, flight, booking, mawb, warehouse, uld, notification) | **Connection Pool tuning**: añadidos `pool-name`, `minimum-idle=2`, `idle-timeout=600000`, `max-lifetime=1800000`, `leak-detection-threshold=30000` (además de `connection-timeout` + `maximum-pool-size` ya presentes) |
| `backend/.../exportservice/resources/application.properties` | `maximum-pool-size=5`, `minimum-idle=1`, `connection-timeout=30000`, `pool-name`, `idle-timeout`, `max-lifetime`, `leak-detection-threshold` (antes solo `read-only=false`) |
| `docker/docker-compose.infrastructure.yml` | Postgres arranca con `command: postgres -c max_connections=150` (7 servicios × pool 10 + export 5 ≈ 65 conns; margen para crecer) |

Nota CDN: pendiente a nivel de infraestructura (Cloudflare/CloudFront delante de nginx); nginx ya sirve `/assets/` con `Cache-Control: public, immutable` (frontend/nginx.conf).

## Recent session changes (July 26, 2026 — Receipt Correction Logic Fix)

| File | Change |
|------|--------|
| `frontend/src/views/WarehouseReceiptsView.vue` | **CRITICAL FIX**: `executeEmit()` now calls `updateEmit` (PUT /{id}/emit) instead of `createCorrection` (POST /{id}/correct) when editing existing receipts. Edits now UPDATE the same receipt in-place — same ID, same Excel/PDF, same audit trail. UI labels reverted to "Actualizar Recibo" / "Modo edicion". Removed edit icon button and amber highlight. |
| `backend/.../service/WarehouseService.java` | `copyReceiptFields()` no longer sets `pdfData(null)` / `excelData(null)`. These fields are only null on fresh creates (new entity). On updates, the existing persisted artifacts remain until async `generatePersistedArtifacts` regenerates them after commit. |
| `backend/.../controller/WarehouseController.java` | `PUT /{receiptId}/emit` audit log now includes `actualKg`, `actualLbs`, `chargeableKg`, `chargeableLbs` for full traceability. |

## Recent session changes (July 26, 2026 — Receipt Correction Superseding System)

| File | Change |
|------|--------|
| `backend/.../entity/WarehouseReceipt.java` | Added `correctionOfId` (UUID FK), `correctionNumber` (Integer, default 1), `superseded` (Boolean, default false) |
| `backend/.../dto/WarehouseReceiptDTO.java` | Added `correctionOfId`, `correctionNumber`, `superseded` fields + entity mapping |
| `backend/.../repository/WarehouseReceiptRepository.java` | Added `findBySupersededFalse()`, `findByAirlineIdAndSupersededFalse(UUID)`, `findByMawbIdAndSupersededFalse(UUID)`, `findByMawbIdOrderByCreatedAtAsc(UUID)`, `supersedeOthers(UUID, UUID)` native query |
| `backend/.../service/WarehouseReceiptServiceImpl.java` | `getAll()` now uses `findBySupersededFalse()` / `findByAirlineIdAndSupersededFalse()` to exclude superseded receipts from GET /api/receipts |
| `backend/.../service/WarehouseService.java` | **NEW**: `createCorrection()` — creates new receipt linked to original, supersedes ALL active receipts for the MAWB; **Changed**: `processWarehouseReceipt` purge branch now marks old receipts `superseded=true` instead of DELETE |
| `backend/.../controller/WarehouseController.java` | **NEW**: `POST /{receiptId}/correct` endpoint calling `createCorrection()` with audit logging (action=RECEIPT_CORRECTION) |
| `backend/.../service/ExportService.java` | Audit export now includes `RECEIPT_CORRECTION` entity type; added `receiptAuditJson()` helper; CSV export includes `details` column |
| `backend/resources/db/migration/V35__add_correction_fields_to_warehouse_receipt.sql` | **NEW** — adds `correction_of_id` (UUID FK), `correction_number` (int DEFAULT 1), `superseded` (boolean DEFAULT false) + index |
| `backend/resources/db/migration/V36__supersede_old_receipts.sql` | **NEW** — retroactive CTE: marks all but the newest general receipt per MAWB as superseded; same for HAWB receipts per (MAWB, HAWB); ensures non-superseded have correction_number >= 1 |
| `database/migrations/V35__add_correction_fields_to_warehouse_receipt.sql` | Synced from backend |
| `database/migrations/V36__supersede_old_receipts.sql` | Synced from backend |
| `backend/.../test/.../WarehouseReceiptServiceImplTest.java` | `getAll_filtersByAirlineId` updated to mock `findByAirlineIdAndSupersededFalse` |
| `frontend/src/api/receipts.js` | Added `createCorrection(receiptId, payload)` → `POST /warehouse/receipts/{id}/correct` |
| `frontend/src/stores/app.js` | `loadReceipts()` default page size 50 → 500 to ensure all receipts available for superseded filtering |
| `frontend/src/views/WarehouseReceiptsView.vue` | **`loadExistingReceiptData()`**: sorts receipts by createdAt, filters `activeReceipts` (non-superseded), prefers general non-superseded; maps `_correctionNumber` + `_isSuperseded`; HAWB receipt map only includes non-superseded; loads `receivedByName`, `receivedBySig`, `receiptDate`, `startDatetime` from source receipt; **`receiptTotals`**: skips `r.superseded`; **`receiptById`**: skips `r.superseded`; **`executeEmit()`**: uses `createCorrection()` instead of `updateEmit()` for existing receipts; **UI**: edit icon on MAWB row, amber highlight for existing receipts, "Crear Nueva Versión" button with correction badge + OBSOLETO indicator, removed separate "Editar Recibo" button; **`initForm()`**: added `receivedByName`, `receivedBySig`, `receiptDate`, `startDatetime`, `_correctionNumber`, `_isSuperseded`; **`calcPiece`/`totalChargeableLbs`**: fixed to use `max(scaleLbs, dimLbs)` directly; **`submitReceipt`**: preserves `receivedByName`/`receivedBySig`/`receiptDate`/`startDatetime` from existing receipt; remarks strip duplicate "— RECIBO GENERAL" suffixes; **`onMounted`**: added else-if branch for already-expanded MAWB on page reload |

## Recent session changes (July 20, 2026 — ULD Barcode Scanning + Dashboard Commodity Fix)

| File | Change |
|------|--------|
| `frontend/src/views/DashboardView.vue` | **FIX**: `mawbDispatchedWeightLbs(mawb, flightId)` now filters ULD-AWB links by flight (`uldIds.has(l.uldId)`), preventing cross-flight piece inflation. Added `flightUldIdSet()` with cache + `watch` invalidation. All callers updated to pass `flightId`. |
| `frontend/src/components/FlightDetail.vue` | **FIX**: Same `mawbDispatchedWeightLbs` and `mawbDispatchedPieces` fix — filter by `flightUlds` set. |
| `frontend/src/stores/app.js` | **FIX**: `dispatchUld()` now uses `mawb.awbNumber` instead of `mawb.id` for ULD-AWB link creation. |
| `.gitignore` | Added `backend/**/target/` to ignore all microservice build artifacts. |
| `backend/.../entity/UldPiece.java` | **NEW** — per-piece tracking entity (uld_id, mawb_id, awb_number, hawb_number, piece_number, source [BARCODE/MANUAL], scanned_by, scanned_at). |
| `backend/.../entity/PieceSource.java` | **NEW** — enum: BARCODE, MANUAL. |
| `backend/.../entity/Uld.java` | Added `@OneToMany(mappedBy="uld", cascade=ALL, orphanRemoval=true)` to `UldPiece`. |
| `backend/.../repository/UldPieceRepository.java` | **NEW** — findByUldId, findByUldIdAndMawbId, countByUldIdAndMawbId, deleteByUldIdAndMawbId, etc. |
| `backend/.../repository/MawbRepository.java` | Added `findByAwbNumber(String)` for scan lookup. |
| `backend/.../repository/HawbRepository.java` | Added `findByHawbNumber(String)` for HAWB scan resolution. |
| `backend/.../repository/UldAwbRepository.java` | Added `findByUldIdAndMawbId(UUID, UUID)`. |
| `backend/.../service/ScanService.java` | **NEW** — lookup (MAWB/HAWB/ULD resolution), registerPiece (creates UldPiece + upserts UldAwb + auto-advance status), undoLastPiece. |
| `backend/.../controller/ScanController.java` | **NEW** — GET `/api/scan/lookup`, POST `/api/scan/piece`, DELETE `/api/scan/piece/last`. |
| `backend/.../dto/ScanLookupDTO.java` | **NEW** — response for lookup (type, awbNumber, pieces info, ULD info). |
| `backend/.../dto/ScanPieceRequest.java` | **NEW** — request for registering a piece (uldId, awbNumber, hawbNumber, source). |
| `backend/.../dto/ScanPieceResult.java` | **NEW** — response for piece registration (success, pieceNumber, totalOnUld, availablePieces). |
| `backend/.../config/SecurityConfig.java` | Added `/api/scan/**` for OPERATIONS, TRAFFIC, LOAD_PLANNER, WAREHOUSE_ASSISTANT, ADMIN, SUPER_USER. |
| `backend/resources/db/migration/V32__create_uld_piece_table.sql` | **NEW** — creates `uld_piece` table with indexes, `piece_source` enum type. |
| `database/migrations/V32__create_uld_piece_table.sql` | Synced from backend. |
| `frontend/src/api/scan.js` | **NEW** — lookup, piece, undoLast API calls. |
| `frontend/src/components/ScanPanel.vue` | **NEW** — scan mode panel: auto-focus input, barcode capture, scan history, undo, ULD number detection, audio/visual feedback, camera placeholder. |
| `frontend/src/views/UldsView.vue` | Integrated ScanPanel: scan toggle button in action bar, scan mode state, `onScanPieceAdded` (auto-creates MAWB row + updates pieces), `onScanPieceRemoved`. |

## Recent session changes (July 3, 2026 — Sites + SuperUser role)

| File | Change |
|------|--------|
| `backend/.../entity/Site.java` | **NEW** — entity with id, code, name, country, isActive |
| `backend/.../dto/SiteDTO.java` | **NEW** — DTO with fromEntity/toEntity mappers |
| `backend/.../repository/SiteRepository.java` | **NEW** — CRUD + findByCode + findByIsActiveTrue |
| `backend/.../service/SiteService.java` | **NEW** — CRUD for site management |
| `backend/.../controller/SiteController.java` | **NEW** — CRUD endpoints, audit logging, SuperUser-only |
| `backend/.../entity/UserRole.java` | Renamed `SUPERVISOR` → `SUPER_USER` |
| `backend/.../entity/AppUser.java` | Added `@ManyToMany(fetch = EAGER)` relationship to `Site` via `user_sites` join table |
| `backend/.../dto/LoginResponse.java` | Added `List<SiteDTO> sites` field |
| `backend/.../dto/AppUserDTO.java` | Added `List<UUID> siteIds` field |
| `backend/.../controller/AuthController.java` | Login now returns user's assigned sites |
| `backend/.../service/AppUserServiceImpl.java` | Handles site assignment on create/update |
| `backend/.../controller/AppUserController.java` | Validates siteIds on create (non-empty) |
| `backend/.../config/SecurityConfig.java` | Renamed `SUPERVISOR` → `SUPER_USER` in all matchers; added `.requestMatchers("/api/sites/**").hasAuthority("SUPER_USER")` |
| `backend/.../service/PermissionService.java` | `SUPERVISOR` → `SUPER_USER` |
| `backend/resources/db/migration/V15__create_sites_table.sql` | **NEW** — creates `site` table, seeds SDQ/STI/PUJ/MIA |
| `backend/resources/db/migration/V16__create_user_sites_table.sql` | **NEW** — creates `user_sites` join table, assigns all users to SDQ |
| `backend/resources/db/migration/V17__rename_supervisor_to_super_user.sql` | **NEW** — updates existing rows from SUPERVISOR → SUPER_USER |
| `database/migrations/V15__create_sites_table.sql` | Synced from backend |
| `database/migrations/V16__create_user_sites_table.sql` | Synced from backend |
| `database/migrations/V17__rename_supervisor_to_super_user.sql` | Synced from backend |
| `backend/resources/db/migration/V8__update_user_role_to_varchar.sql` | Updated seed to use SUPER_USER |
| `backend/resources/db/migration/V10__add_password_hash_and_seed_real_users.sql` | Updated seed to use SUPER_USER |
| `database/migrations/V8__update_user_role_to_varchar.sql` | Synced from backend |
| `database/migrations/V10__add_password_hash_and_seed_real_users.sql` | Synced from backend |
| `frontend/src/api/sites.js` | **NEW** — CRUD API calls for sites |
| `frontend/src/stores/auth.js` | Added `sites`, `selectedSiteId`, `selectedSite`, `confirmSite()`; `isAuthenticated` requires token + site; renamed SUPERVISOR → SUPER_USER |
| `frontend/src/views/LoginView.vue` | **Two-step login**: step 1 credentials, step 2 site selection dropdown; site is mandatory |
| `frontend/src/router/index.js` | Renamed SUPERVISOR → SUPER_USER; added site check in beforeEach guard |
| `frontend/src/components/layout/Sidebar.vue` | Shows selected site code in logo area + site indicator bar |
| `frontend/src/views/SettingsView.vue` | **Redesigned with tabs**: Users tab shows site assignment per user (checkbox list of sites); Sites tab (SuperUser only) for CRUD site management |
| `frontend/src/views/UsersView.vue` | Renamed `SUPERVISOR` → `SUPER_USER` label |

## Recent session changes (June 30, 2026 — READ_ONLY role + ERP redesign)

| File | Change |
|------|--------|
| `backend/.../entity/UserRole.java` | Added `READ_ONLY` enum value |
| `backend/.../config/SecurityConfig.java` | Added `.requestMatchers(HttpMethod.GET, "/api/**")` for READ_ONLY; READ_ONLY has GET-only access to all APIs |
| `backend/.../service/PermissionService.java` | Added `READ_ONLY` case → all views visible |
| `backend/resources/db/migration/V13__add_read_only_role.sql` | **NEW** — no DDL (varchar column already supports new values) |
| `database/migrations/V13__add_read_only_role.sql` | Synced from backend |
| `frontend/src/stores/auth.js` | Added `READ_ONLY` → returns `true` for all views in `canView()` |
| `frontend/src/router/index.js` | Added `READ_ONLY` → returns `true` for all views in `hasPermission()` |
| `frontend/src/views/SettingsView.vue` | **Complete redesign**: modal-based editing (replaced inline edit row), search/filter input, role list includes READ_ONLY, removed `roleColor()` function (no more colored role badges — all roles use neutral `bg-slate-100 text-slate-600`), toolbar with user count |
| `frontend/src/views/UsersView.vue` | **Complete redesign**: connected users now rendered as proper table (not cards), audit log uses monochrome action badges (removed `actionStyle()` colors), added READ_ONLY role label |
| `frontend/src/api/flights.js` | Removed UPS UUID hardcode — `getAll()` no longer filters by `airlineId: UPS`, create/update accept DTO as-is |

## Architecture (June 2026)

### RBAC (Role-Based Access Control)

Seven roles with the following view permissions (enforced on both frontend routes and backend endpoints):

| Role | Dashboard | Bookings | Receipts | Flights | MAWBs | Load Planning | ULDs | Users | Settings |
|------|-----------|----------|----------|---------|-------|---------------|------|-------|----------|
| **Read Only** | ✅ | ✅* | ✅* | ✅* | ✅* | ✅* | ✅* | ❌ | ❌ |
| **Warehouse Assistant** | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Operations** | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Traffic** | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Load Planner** | ✅ | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ |
| **Admin** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| **SuperUser** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |


\* READ_ONLY can see all views (GET-only) but cannot create/update/delete — enforced at backend SecurityConfig via `HttpMethod.GET` matcher.

### Authentication Flow
1. User POSTs email to `/api/auth/login` → backend returns JWT + user info
2. Token stored in `localStorage` under key `aircargo_auth` (persisted across refreshes)
3. Axios interceptor attaches `Authorization: Bearer <token>` to every request
4. Spring Security `JwtAuthFilter` validates token on every request (except `/api/auth/**`)
5. 401 auto-clears localStorage and redirects to `/login`

### Test Users (seeded by V9 migration)

| Email | Role |
|-------|------|
| `readonly@aircargo.com` | READ_ONLY |
| `warehouse@aircargo.com` | WAREHOUSE_ASSISTANT |
| `operations@aircargo.com` | OPERATIONS |
| `traffic@aircargo.com` | TRAFFIC |
| `loadplanner@aircargo.com` | LOAD_PLANNER |
| `admin@aircargo.com` | ADMIN |
| `supervisor@aircargo.com` | SUPER_USER |

### Caching
- Spring Cache with Caffeine (in-process, configurable via `spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=300s`)
- **Flights** (`getAll` + `getById`): `@Cacheable(value = "flights")`, evicted on create/update/delete
- **Airlines** (`getAll` + `getById`): `@Cacheable(value = "airlines")`, evicted on create/update/delete

### Async / Event-Driven
- `@EnableAsync` + `@EnableCaching` on main application class
- `ReceiptCreatedEvent` published after receipt creation in `WarehouseService`
- `ReceiptEventListener` (async) processes background tasks post-receipt
- Template for future: export PDFs, send notifications, cache warming

### Authentication Implementation
- **Backend**: `JwtUtil` (jjwt 0.12.6), `JwtAuthFilter` (OncePerRequestFilter), `SecurityConfig` (filter chain with URL-based role checks), `AuthController` (POST `/api/auth/login`)
- **Frontend**: `stores/auth.js` (Pinia store + localStorage persistence), `views/LoginView.vue` (email-only login), `router/index.js` (navigation guards with role-based permission checks), `api/client.js` (token injection interceptor + 401 auto-redirect)
- Tests use `@Profile("test")` security config that permits all requests

## Backend quirks

- **Spring Security** (`spring-boot-starter-security`, `jjwt-api/impl/jackson 0.12.6`) — protects all `/api/*` endpoints by role via `SecurityConfig.filterChain()`.
- **Test pattern:** All 63 tests pass (`mvn test`): service-layer tests use Mockito (no Spring context); integration tests use `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` with H2 (test profile disables security via `TestSecurityConfig`). Flyway disabled in tests.
- **Apache POI** for Excel ramp manifest parsing (`LoadPlanningImportService`).
- **Fixes applied:**
  - Deleted duplicate `com.aircargo.WebConfig` (root package) — kept `com.aircargo.config.WebConfig` (ports 5173 + 5174) since both beans named `webConfig` caused `ConflictingBeanDefinitionException`.
  - Removed `@CrossOrigin(origins = "*")` from 5 controllers (Hawb, Warehouse, Mawb, FlightManifest, LoadPlanning) — conflicted with `WebConfig.allowCredentials(true)` causing CORS rejection.
  - Added `= BigDecimal.ZERO` default to `Booking.reservedKg` and null guard in `BookingDTO.toEntity()` — field was `nullable = false` but had no default, causing 500 on booking create.

## Frontend quirks

- **Stale files (removed):** `src/stores/ulds.js` is the active store. No TypeScript files exist in the frontend.
- **No frontend tests** exist (no vitest/jest config found).
- `README.md` is the default Vite template — ignore it.

## Recent session changes (June 2026)

| File | Change |
|------|--------|
| `frontend/src/views/WarehouseReceiptsView.vue` | Major redesign: Step 1 two-column layout (left: data fields, right: HAWB table + checkboxes group); shipper/consignee preloaded from MAWB, editable, sync'd to backend on blur; 5 checkboxes (Cash Only, Booked in ACOMS, Documents Provided, Export Customs Completed, Pre-built) grouped in bordered card; Step 2 professional dark-header table with bordered inputs, proper spacing; multi-HAWB pieces section with per-HAWB tables; Step 5 compact signatures (grid 2-col, smaller pads); added PDF + HTML download buttons for supporting evidence; added MAWB-level evidence manager modal (folder icon in status column); **NEW**: HAWB count input with `syncHawbCount()` to add/remove HAWB rows dynamically; fully editable HAWB table (hawbNumber, consignee, pieces, weightKg, destination); filename read from `Content-Disposition` header |
| `frontend/src/api/mawbs.js` | Added `update(mawbId, dto)`, `getSupportingDocs(id)`, `updateSupportingDocs(id, docs)`, `getSupportingDocsPdf(id)` |
| `frontend/src/api/receipts.js` | Added `getSupportingDocsJson(id)`, `getSupportingDocsHtml(id)`, `getSupportingDocsPdf(id)` |
| `backend/aircargo-api/src/main/java/com/aircargo/controller/MawbController.java` | Added `PUT /{mawbId}`, `GET /{mawbId}/supporting-docs`, `PUT /{mawbId}/supporting-docs`, `GET /{mawbId}/supporting-docs/pdf` — MAWB evidence CRUD + PDF generation |
| `backend/aircargo-api/src/main/java/com/aircargo/controller/WarehouseController.java` | Added `ReceiptPayload.supportingDocs` field; `GET /{receiptId}/supporting-docs` (JSON), `GET /{receiptId}/supporting-docs/html`, `GET /{receiptId}/supporting-docs/pdf`; updated `emit` to pass supportingDocs to service; fixed Excel filename to `RECIBO_DE_BODEGA_AWB {mawbNumber}.xlsx` |
| `backend/aircargo-api/src/main/java/com/aircargo/entity/WarehouseReceipt.java` | Added `supportingDocs` text column (default `"[]"`) |
| `backend/aircargo-api/src/main/java/com/aircargo/entity/Mawb.java` | Added `supportingDocs` text column (default `"[]"`) |
| `backend/aircargo-api/src/main/java/com/aircargo/service/WarehouseService.java` | Overloaded `processWarehouseReceipt` to accept `List<Map<String,String>> supportingDocs`; stores as JSON; added `generateSupportingDocsHtml()` + `generateSupportingDocsPdf()` |
| `backend/aircargo-api/src/main/java/com/aircargo/service/PdfGenerationService.java` | New — uses openhtmltopdf to convert HTML+CSS (with embedded base64 images) to PDF |
| `backend/aircargo-api/src/main/java/com/aircargo/repository/WarehouseReceiptRepository.java` | Added `findByMawbId(UUID)` |
| `backend/aircargo-api/pom.xml` | Added `openhtmltopdf-pdfbox:1.0.10` dependency |
| `backend/aircargo-api/src/main/resources/db/migration/V6__add_supporting_docs_to_receipt.sql` | New — `ALTER TABLE warehouse_receipt ADD COLUMN supporting_docs text` |
| `backend/aircargo-api/src/main/resources/db/migration/V7__add_supporting_docs_to_mawb.sql` | New — `ALTER TABLE mawb ADD COLUMN supporting_docs text` |
| `database/migrations/V6__add_supporting_docs_to_receipt.sql` | Synced from backend |
| `database/migrations/V7__add_supporting_docs_to_mawb.sql` | Synced from backend |
| `backend/aircargo-api/src/main/java/com/aircargo/service/ReceiptExportService.java` | **Rewritten** — matches reference template `RECIBO_DE_BODEGA_AWB.xlsx`: same merged cells layout (B-C labels, D-E values, F-G checkboxes), same column widths (A-L), 25 data rows, `Dim Weight` = vol/366 (KGS), `Dim LBS` = vol/194; Tahoma font |
| `frontend/src/views/WarehouseReceiptsView.vue` | **HAWB section redesigned**: bigger (text-[10px] inputs), dark green border-2 border-emerald-800, emerald header bg, emerald-800 accent checkboxes group with shadow |
| `frontend/src/views/BookingsView.vue` | **MAWB status column** added (col-span-2): shows real MAWB status per booking with colored square indicator; stats now use MAWB status for Received %; flujograma reduced to col-span-2 with `h-1.5 w-1.5` squares (no rounded-full) |
| `backend/aircargo-api/src/main/java/com/aircargo/service/WarehouseService.java` | **PDF evidence rewritten**: replaced CSS grid + object-fit with table layout; max image size reduced to 150KB; removed unsupported CSS properties for openhtmltopdf compatibility; added `xmlEscape()` helper |
| `backend/aircargo-api/src/main/java/com/aircargo/controller/MawbController.java` | **MAWB PDF evidence rewritten**: same table layout as receipt PDF; added `xmlEscape()` helper; replaced flex/grid with table; reduced image max to 150KB; replaced `→` unicode with `&#8594;` |
| `backend/aircargo-api/src/main/java/com/aircargo/controller/MawbController.java` | Fixed XML/HTML entities for openhtmltopdf: self-closing `<meta/>`, `&mdash;` → `&#8212;`; added `xmlEscape()` helper for all dynamic content to prevent SAXParseException on `&` in user data |
| `backend/aircargo-api/src/main/java/com/aircargo/service/WarehouseService.java` | Fixed XML/HTML entities for openhtmltopdf: self-closing `<meta/>`, `&mdash;` → `&#8212;`, `&middot;` → `&#183;`; added `xmlEscape()` helper for all dynamic content |
| `backend/aircargo-api/src/main/java/com/aircargo/service/PdfGenerationService.java` | **Rewritten** — decodes base64 data URIs in HTML to temp files with `file:///` paths for reliable PDF image rendering; auto-cleanup in `finally` block |
| `backend/aircargo-api/src/main/java/com/aircargo/service/WarehouseService.java` | Simplified `generateSupportingDocsPdf()` — removed temp file handling (now delegated to `PdfGenerationService`) |
| `frontend/src/views/WarehouseReceiptsView.vue` | **HAWB inputs enlarged**: `text-[10px]` → `text-xs`, wider columns (w-20→w-24, w-14→w-16), bigger padding; **Labels darkened**: all form `<label>` elements `text-slate-400` → `text-slate-700`; secondary text darkened to `text-slate-500`; step headers darkened; evidence upload/camera labels darkened; pieza count text darkened |
| `backend/aircargo-api/src/main/java/com/aircargo/service/ReceiptExportService.java` | **Rewritten** — matches reference template `RECIBO_DE_BODEGA_AWB.xlsx`: same merged cells layout (B-C labels, D-E values, F-G checkboxes), same column widths (A-L), 25 data rows, `Dim Weight` = vol/366 (KGS), `Dim LBS` = vol/194; Tahoma font; **Added Evidencias sheet**: signatures (dock/deliveredBy/broker) + supporting docs embedded as images; **Sheet protection**: main sheet protected with password `aircargo2024`, value cells unlocked; uses `ObjectMapper` to parse `supportingDocs` JSON |
| `frontend/src/views/MawbsView.vue` | **Complete redesign**: 6 frozen columns (MAWB/Shipper/Pzas/Kg/Dest/Pcs Disp) with sticky positioning; date headers enlarged `text-[6px]` → `text-[11px]` bold; all table content `text-[10px]` → `text-xs`; piece cells use **chalkboard texture** (dark green gradient + line noise + `radial-gradient`), white chalk-like text with `text-shadow` glow; **SVG mini arc** showing piece distribution per flight; **pop-in animation** on cell entrance with staggered delays; **column hover glow** via `ring-1 ring-inset ring-white/20`; **minimap widget** (teleported) with viewport overlay and `@scroll` tracking; MAWB click navigates to `/receipts?mawbId=xxx` using router push + query params; `WarehouseReceiptsView` reads `route.query.mawbId` on mount to auto-expand the target MAWB; `float button` toggle for minimap visibility |
| `frontend/src/views/MawbsView.vue` | **June 22 updates**: Removed St column; added Pcs Dispatched column (right-aligned, amber ⚠ when > received); Shipper + Consignee stacked vertically in same column; MAWB cell gets status-based background + left border color (gray=BOOKED, blue=RECEIVED, amber=MANIFESTED, emerald=DEPARTED) with Spanish status text subtitle; `totalPieces` capped at warehouse received quantity (`Math.min(dispatched, received)`); per-ULD breakdown in tooltip + "N ULDs" subtitle when >1 ULD per flight; stats bar includes Despachadas count with exceso indicator |
| `frontend/src/views/UldsView.vue` | **Autocomplete MAWB**: replaced `<select>` with text input + filtered suggestions dropdown; keyboard navigation (arrows + enter + escape); MAWB search filters by awbNumber/shipperName; pending pieces logic (receipt > booking) |
| `frontend/src/views/DashboardView.vue` | **Fixed tare formulas**: `netLbs` = gross - realTare (was gross - bellyTare); `payloadLbs` = netLbs (was netLbs + 5 dummy) |
| `frontend/src/components/layout/Sidebar.vue` | **Icon swap**: `IconPackage` → `IconPackageExport` for ULDs; all nav icons increased 16→18 (+10%) |
| `backend/aircargo-api/src/main/java/com/aircargo/service/WarehouseService.java` | **Piece accumulation fix + Booking MAWB sync**: `processWarehouseReceipt()` now deletes existing receipt+pieces for a MAWB before re-emitting; after saving, updates linked Booking's `awbNumber` from MAWB |
| `backend/aircargo-api/src/main/java/com/aircargo/repository/BookingRepository.java` | Added `findByMawbId(UUID)` for booking lookup by MAWB |
| `backend/aircargo-api/src/main/java/com/aircargo/service/ReceiptExportService.java` | Fixed `Workbook` → `XSSFWorkbook` cast error in `createEvidenceSheet` call |
| All 10 `src/views/*View.vue` + `WarehouseForm.vue` | **Font-size standardisation**: table data → `text-[10px]`, table headers → `text-[11px]`, titles → `text-[12px]` |
| `frontend/src/views/WarehouseReceiptsView.vue` | **Pieces loaded from existing receipt**: when editing a receipt, existing pieces are now loaded via `receiptsApi.getPieces()` and displayed in the form; `editReceipt` rewritten to send pieces+evidence via POST `/emit` (backend replaces old receipt entirely); signatures (dock/deliveredBy/broker) from existing receipt shown as images in MAWB evidence section; **Signature evidence enriched**: each signature image now has a companion text card showing the person's name and ID (printName, deliveredByName+ID, brokerName+ID) in the MAWB evidence grid |
| `frontend/src/api/receipts.js` | Added `getPieces(id)` — calls `GET /warehouse/receipts/{id}/pieces` |
| `frontend/src/views/MawbsView.vue` | **Pcs Reserved from Booking**: now reads from `Booking.skids` (via `store.bookings`) instead of `Mawb.pieces`; falls back to `Mawb.pieces` if no booking linked. **Cap removed**: per-flight pieces are now the raw total from ULD-AWB links (no capping by received/dispatched ratio). **ULD count always shown**: each flight cell always shows `N ULDs` below the piece count. **Tooltip changed**: shows "X UPS REPARTIDAS ENTRE Y ULDs" summary. **Bookings loaded**: added `store.loadBookings()` in `onMounted` and `onFlightChange` so booking data is available for matrix building. |

## Recent session changes (June 29, 2026 — Password + Real Users)

| File | Change |
|------|--------|
| `backend/.../entity/AppUser.java` | Added `passwordHash` varchar(255) field (nullable) |
| `backend/.../dto/LoginRequest.java` | Added optional `password` field |
| `backend/.../dto/LoginResponse.java` | Added `hasPasswordSet` boolean field |
| `backend/.../dto/SetPasswordRequest.java` | **NEW** — record with email, newPassword, currentPassword |
| `backend/.../controller/AuthController.java` | Login now verifies password via BCrypt if `passwordHash` is set; returns 428 if password required but missing; added `POST /api/auth/set-password` (sets or changes password, returns JWT) |
| `backend/resources/db/migration/V10__add_password_hash_and_seed_real_users.sql` | **NEW** — adds `password_hash` column, seeds 6 real users: jsantos@rannik.com (ADMIN), esantana@rannik.com (SUPERVISOR → SUPER_USER), dchestaro@rannik.com (OPERATIONS), ilsantana@rannik.com (WAREHOUSE_ASSISTANT), earellano@ups.com (TRAFFIC), jcastrolopez@ups.com (LOAD_PLANNER) — all with null password_hash |
| `database/migrations/V10__add_password_hash_and_seed_real_users.sql` | Synced from backend |
| `frontend/src/api/auth.js` | Added `setPassword(email, newPassword, currentPassword)` |
| `frontend/src/stores/auth.js` | Added `hasPasswordSet` ref (persisted); `login()` now accepts optional password parameter |
| `frontend/src/views/LoginView.vue` | Added password input (shown when backend returns 428); "Establecer contraseña" link redirects to /set-password |
| `frontend/src/views/SetPasswordView.vue` | **NEW** — first-time password setup page; fields: email (readonly from query), currentPassword (only if has existing password), newPassword, confirm; validates match + min 6 chars; auto-redirects to /login after success |
| `frontend/src/router/index.js` | Added `/set-password` route; both `/login` and `/set-password` marked as `publicPaths` (no auth required) |

## Recent session changes (June 29, 2026 — Settings + Users Views + Audit)

| File | Change |
|------|--------|
| `backend/.../AircargoApiApplication.java` | Added `@EnableScheduling` for `ActiveSessionTracker` purge |
| `backend/.../auth/UserPrincipal.java` | Added `fullName` field to record |
| `backend/.../auth/JwtUtil.java` | `generateToken()` now accepts `fullName` parameter, stores as JWT claim |
| `backend/.../auth/JwtAuthFilter.java` | Extracts `fullName` from JWT claims when building `UserPrincipal` |
| `backend/.../entity/AuditLog.java` | **NEW** — entity with userId, email, fullName, action, entityType, entityId, details, ipAddress, createdAt |
| `backend/.../dto/AuditLogDTO.java` | **NEW** — DTO with `fromEntity()` mapper |
| `backend/.../dto/ConnectedUserDTO.java` | **NEW** — DTO for connected users (userId, email, fullName, role, lastHeartbeat, lastLogin) |
| `backend/.../repository/AuditLogRepository.java` | **NEW** — findByUserId, findAllByOrderByCreatedAtDesc, findByAction |
| `backend/.../service/AuditService.java` | **NEW** — helper methods: log, logLogin, logUserCreate, logUserUpdate, logUserDelete, logPasswordReset |
| `backend/.../service/ActiveSessionTracker.java` | **NEW** — in-memory `ConcurrentHashMap` tracking user heartbeats; `@Scheduled` purge every 60s; 5min timeout |
| `backend/.../controller/AuditLogController.java` | **NEW** — `GET /api/audit-logs` with optional `userId` filter |
| `backend/.../controller/AuthController.java` | Added `auditService.logLogin()` on login; added `auditService.log()` on set-password; added `GET /api/auth/heartbeat` for session tracking |
| `backend/.../controller/AppUserController.java` | Added audit logging on create/update/delete; added `POST /api/users/{id}/reset-password` (clears hash); added `GET /api/users/connected`; prevent self-delete |
| `backend/.../service/AppUserService.java` | Added `resetPassword(UUID id)` to interface |
| `backend/.../service/AppUserServiceImpl.java` | `create()` sets null password_hash; `update()` preserves existing password_hash; `resetPassword()` clears it |
| `backend/.../config/SecurityConfig.java` | Added `.requestMatchers("/api/audit-logs/**").hasAnyAuthority("ADMIN", "SUPERVISOR")` (later renamed to `SUPER_USER`) |
| `backend/.../resources/db/migration/V11__create_audit_log.sql` | **NEW** — CREATE TABLE `audit_log` with indexes on userId, action, createdAt, entity |
| `database/migrations/V11__create_audit_log.sql` | Synced from backend |
| `frontend/src/api/users.js` | **NEW** — all user CRUD + resetPassword + getConnected + getAuditLogs + heartbeat |
| `frontend/src/views/SettingsView.vue` | **NEW** — user management: list/create/edit/delete users, reset passwords, role assignment, active toggle |
| `frontend/src/views/UsersView.vue` | **NEW** — connected users (live cards with green dot) + audit log table with user filter + action colors |
| `frontend/src/router/index.js` | Added `/users` → UsersView + `/settings` → SettingsView routes |
| `frontend/src/components/layout/Header.vue` | Added `/settings`: 'Configuración' title |
| `frontend/src/App.vue` | Heartbeat interval (60s) calls `GET /api/auth/heartbeat` when authenticated |

## Recent session changes (June 29, 2026 — Audit extendido a todos los controladores)

| File | Change |
|------|--------|
| `frontend/src/App.vue` | Heartbeat interval (60s) calls `GET /api/auth/heartbeat` when authenticated |
| `backend/.../controller/FlightController.java` | Added `AuditService` injection + `@AuthenticationPrincipal` + `HttpServletRequest`; audit logging on create/update/delete with null-safe principal check |
| `backend/.../controller/BookingController.java` | Added `AuditService` injection + `@AuthenticationPrincipal` + `HttpServletRequest`; audit logging on create/update/delete/updateAwb with null-safe principal check |
| `backend/.../controller/MawbController.java` | Added `AuditService` injection + `@AuthenticationPrincipal` + `HttpServletRequest`; audit logging on createMawb, updateMawb, updateMawbStatus, updateSupportingDocs with null-safe principal check |
| `backend/.../controller/UldController.java` | Added `AuditService` injection + `@AuthenticationPrincipal` + `HttpServletRequest`; audit logging on create, update, assignFlight, transferUld, delete with null-safe principal check |
| `backend/.../controller/WarehouseController.java` | Added `AuditService` injection + `@AuthenticationPrincipal` + `HttpServletRequest`; audit logging on emitWarehouseReceipt, updateWarehouseReceipt with null-safe principal check |
| `backend/.../test/.../BookingControllerTest.java` | Updated: replaced `@Mock AuditService` with `@Mock AuditLogRepository` + real `AuditService` (bytebuddy/Java 25 compat) |
| `backend/.../test/.../BookingControllerIntegrationTest.java` | Updated: passes null principal to skip audit (filter chain clears SecurityContext) |

Entity types auditadas: `FLIGHT`, `BOOKING`, `MAWB`, `ULD`, `RECEIPT`. Cada create/update/delete queda registrado en `audit_log` con usuario, acción, entidad, detalles JSON e IP. El principal se verifica con null-safety para compatibilidad con tests y edge cases.

## Build

```sh
npm run build         # Vite build succeeds
```

## Recent session changes (June 29, 2026 — RBAC + Cache + Async)

| File | Change |
|------|--------|
| `backend/pom.xml` | Added `spring-boot-starter-security`, `jjwt-api/impl/jackson 0.12.6`, `spring-boot-starter-cache`, `caffeine` |
| `backend/.../entity/UserRole.java` | Replaced 4 old roles with 6 new roles: `WAREHOUSE_ASSISTANT`, `OPERATIONS`, `TRAFFIC`, `LOAD_PLANNER`, `ADMIN`, `SUPERVISOR` (later renamed to `SUPER_USER`) |
| `backend/.../entity/AppUser.java` | Removed `columnDefinition = "user_role"` (now varchar), default `WAREHOUSE_ASSISTANT` |
| `backend/.../auth/JwtUtil.java` | **NEW** — HMAC-SHA512 JWT generation/validation via jjwt 0.12.6 |
| `backend/.../auth/UserPrincipal.java` | **NEW** — `record` carrying userId, role, airlineId, email |
| `backend/.../auth/JwtAuthFilter.java` | **NEW** — `OncePerRequestFilter` extracts JWT from Bearer header, sets SecurityContext |
| `backend/.../config/SecurityConfig.java` | **NEW** — URL-based role checks per view (`@Profile("!test")`) |
| `backend/.../controller/AuthController.java` | **NEW** — `POST /api/auth/login` returns JWT + user info |
| `backend/.../dto/LoginRequest.java` | **NEW** — `record` with email |
| `backend/.../dto/LoginResponse.java` | **NEW** — `record` with token, user info |
| `backend/.../service/PermissionService.java` | **NEW** — role→view mapping for programmatic checks |
| `backend/.../event/ReceiptCreatedEvent.java` | **NEW** — record published after receipt creation |
| `backend/.../event/ReceiptEventListener.java` | **NEW** — `@Async` + `@EventListener` for background processing |
| `backend/.../AircargoApiApplication.java` | Added `@EnableCaching`, `@EnableAsync` |
| `backend/.../service/FlightServiceImpl.java` | Added `@Cacheable("flights")` on getAll, `@CacheEvict` on create/update/delete |
| `backend/.../service/AirlineServiceImpl.java` | Added `@Cacheable("airlines")` on getAll/getById, `@CacheEvict` on create/update/delete |
| `backend/.../service/WarehouseService.java` | Publishes `ReceiptCreatedEvent` after receipt save; added `pdfService` + `eventPublisher` fields |
| `backend/.../config/WebConfig.java` | Added `CorsConfigurationSource` bean for Spring Security compatibility |
| `backend/.../repository/AppUserRepository.java` | Added `findByEmail(String)` + `existsByEmail(String)` |
| `backend/resources/db/migration/V8__update_user_role_to_varchar.sql` | **NEW** — converts `role` column from enum to varchar(50), drops type, seeds 3 users |
| `backend/resources/db/migration/V9__seed_more_users.sql` | **NEW** — seeds warehouse, operations, admin users |
| `database/migrations/V8__update_user_role_to_varchar.sql` | Synced from backend |
| `database/migrations/V9__seed_more_users.sql` | Synced from backend |
| `backend/.../config/TestSecurityConfig.java` | **NEW** — `@Profile("test")` permits all requests for integration tests |
| `frontend/src/api/client.js` | Added request interceptor (Bearer token from localStorage) + 401 auto-redirect |
| `frontend/src/api/auth.js` | **NEW** — `authApi.login(email)` |
| `frontend/src/stores/auth.js` | **NEW** — Pinia auth store with localStorage persistence, `canView()` permission check |
| `frontend/src/views/LoginView.vue` | **NEW** — email-only login form |
| `frontend/src/router/index.js` | Added `/login` route, `beforeEach` guards (auth check + role permission check) |
| `frontend/src/App.vue` | Renders `<Sidebar>` only when authenticated; login view when not |
| `frontend/src/components/layout/Sidebar.vue` | Dynamic nav items by `canView()`, real user info from auth store, logout button |

## Recent session changes (July 28, 2026 — Phase 6: Warehouse Service Extraction: services migrated)

| File | Change |
|------|--------|
| `backend/.../entity/WarehouseReceipt.java` | Added `mawbNumber` field (varchar 50) for flat AWB number storage (no cross-service entity dep) |
| `backend/.../dto/WarehouseReceiptDTO.java` | Added `mawbNumber` field + `fromEntity`/`toEntity` mapping |
| `backend/.../resources/db/migration/V1__create_warehouse_tables.sql` | Added `mawb_number` column to `warehouse_receipt` table |
| `backend/.../resources/templates/dock-receipt-template.xlsx` | Copied from monolith for Excel export template |
| `backend/.../resources/fonts/JetBrainsMonoNerdFontMono-Regular.ttf` | Copied from monolith for PDF rendering |
| `backend/.../service/PdfGenerationService.java` | **NEW** — adapted from monolith (openhtmltopdf PDF generator) |
| `backend/.../service/ExcelExportStyles.java` | **NEW** — adapted from monolith (Apache POI cell styles) |
| `backend/.../service/EvidenceSheetRenderer.java` | **NEW** — adapted from monolith (evidence sheet in XLSX, uses `mawbNumber` instead of `mawb.getAwbNumber()`) |
| `backend/.../service/ReceiptExportService.java` | **NEW** — adapted from monolith (XLSX export from template, no cache annotations) |
| `backend/.../service/ReceiptFullPdfService.java` | **NEW** — adapted from monolith (full receipt PDF with HTML builder, uses `mawbNumber` instead of `mawb.getAwbNumber()`) |
| `backend/.../service/WarehouseServiceImpl.java` | **UPDATED** — wires PdfGenerationService, ReceiptExportService, ReceiptFullPdfService; replaced all stubs (`getSupportingDocsHtml`, `getSupportingDocsPdf`, `exportReceipt`, `getReceiptPdf`, `generatePersistedArtifacts`) with real implementations; fetches `mawbNumber` via MawbClient on emit |
| `backend/.../controller/WarehouseReceiptController.java` | **UPDATED** — added `@AuthenticationPrincipal`, `HttpServletRequest`, + audit logging on create/update/delete |
| `backend/.../controller/WarehouseController.java` | **UPDATED** — added audit logging to `updateReceipt` |
| `backend/.../config/SecurityConfig.java` | **UPDATED** — added READ_ONLY GET access alongside WAREHOUSE_ASSISTANT/ADMIN/SUPER_USER |
| `backend/.../service/WarehouseServiceImpl.java` | **UPDATED** — `ReceiptCreatedEvent` now includes `mawbNumber` |
| `frontend/vite.config.js` | **CHANGED** — proxy target `localhost:9091` → `localhost:8080` (gateway) |
| `AGENTS.md` | **UPDATED** — Phase 6 status → ✅ Complete |

## Recent session changes (July 28, 2026 — Phase 7: ULD Service Extraction: complete microservice)

| File | Change |
|------|--------|
| `backend/.../uldservice/entity/Uld.java` | **NEW** — entity with flat UUID FK fields (no JPA relationships), `net_weight_lbs` persistable (removed `insertable=false`) |
| `backend/.../uldservice/entity/UldAwb.java` | **NEW** — entity with computed columns (`lapse_minutes`, `pcs_per_min`, etc.) and CommodityType from common |
| `backend/.../uldservice/entity/UldPiece.java` | **NEW** — per-piece tracking entity (BARCODE/MANUAL source, scanned_by UUID) |
| `backend/.../uldservice/entity/UldTypeConfig.java` | **NEW** — entity for default tare weights per ULD type per airline |
| `backend/.../uldservice/entity/UldStatus.java` | **NEW** — enum: OPEN, BUILT, SEALED, LOADED, OFFLOADED, LEFT_BEHIND |
| `backend/.../uldservice/entity/UldType.java` | **NEW** — enum: PMC, PAH, PAG, PAJ, AAY, AAZ, AAD, PIP, BULK, AMP, AMJ |
| `backend/.../uldservice/entity/PieceSource.java` | **NEW** — enum: BARCODE, MANUAL |
| `backend/.../uldservice/dto/UldDTO.java` | **NEW** — DTO with UldAwbDTO children (awbs list), fromEntity/toEntity mappers |
| `backend/.../uldservice/dto/UldAwbDTO.java` | **NEW** — DTO with all fields + entity mapping |
| `backend/.../uldservice/dto/ScanLookupDTO.java` | **NEW** — response for scan lookup (type, MAWB/ULD info, piece counts) |
| `backend/.../uldservice/dto/ScanPieceRequest.java` | **NEW** — request for piece registration (uldId, awbNumber, hawbNumber, source) |
| `backend/.../uldservice/dto/ScanPieceResult.java` | **NEW** — response for piece registration (success, pieceNumber, totalOnUld) |
| `backend/.../uldservice/dto/TransferRequest.java` | **NEW** — request for ULD transfer (destinationFlightId, reason) |
| `backend/.../uldservice/dto/PageResponse.java` | **NEW** — generic paginated response wrapper |
| `backend/.../uldservice/repository/UldRepository.java` | **NEW** — CRUD + findByAirlineId, findByFlightId, findByUldNumber |
| `backend/.../uldservice/repository/UldAwbRepository.java` | **NEW** — CRUD + findByUldId, findByMawbId, findByUldIdAndMawbId, findByUldIdIn |
| `backend/.../uldservice/repository/UldPieceRepository.java` | **NEW** — CRUD + findByUldId, findByUldIdAndMawbId, countByUldIdAndMawbId, findFirstByUldIdAndMawbIdOrderByPieceNumberDesc, deleteByUldIdAndMawbId |
| `backend/.../uldservice/repository/UldTypeConfigRepository.java` | **NEW** — CRUD + findByAirlineId, findByAirlineIdAndUldType |
| `backend/.../uldservice/service/UldService.java` | **NEW** — interface: getAll, getById, create, update, delete, transferUld, assignFlight |
| `backend/.../uldservice/service/UldServiceImpl.java` | **NEW** — computeMetricWeights (lbs↔kg), enrichWithAwbs, transfer with audit note |
| `backend/.../uldservice/service/UldAwbService.java` | **NEW** — interface: getAll, getById, create, update, delete |
| `backend/.../uldservice/service/UldAwbServiceImpl.java` | **NEW** — ULD existence validation, flat DTO↔entity |
| `backend/.../uldservice/service/ScanService.java` | **NEW** — lookup (MAWB/HAWB/ULD via Feign), registerPiece (creates UldPiece + upserts UldAwb), undoLastPiece, normalizeCode |
| `backend/.../uldservice/controller/UldController.java` | **NEW** — CRUD + GET with airline/flight filter, pagination, PUT transfer, PUT assignFlight, DELETE |
| `backend/.../uldservice/controller/UldAwbController.java` | **NEW** — CRUD + GET with uldId/mawbId filter |
| `backend/.../uldservice/controller/ScanController.java` | **NEW** — GET /api/scan/lookup, POST /api/scan/piece, DELETE /api/scan/piece/last |
| `backend/.../uldservice/controller/UldTypeConfigController.java` | **NEW** — CRUD + GET by airline |
| `backend/.../uldservice/config/SecurityConfig.java` | **NEW** — JWT filter, CORS, role-based access (READ_ONLY GET, OPERATIONS/TRAFFIC/LOAD_PLANNER/ADMIN/SUPER_USER mutations), @Profile("!test") |
| `backend/.../uldservice/config/CacheConfig.java` | **NEW** — @EnableCaching, Caffeine cache for ulds and uld-awbs |
| `backend/.../uldservice/config/ScanEventListener.java` | **NEW** — SSE event emitter per flightId on piece registration |
| `backend/.../uldservice/UldServiceApplication.java` | **NEW** — @SpringBootApplication, @EnableFeignClients, @EnableCaching |
| `backend/.../uldservice/pom.xml` | **NEW** — Spring Boot 3.3, feign-clients, cache, caffeine, postgresql, flyway |
| `backend/.../uldservice/Dockerfile` | **NEW** — multi-stage build for container deployment |
| `backend/.../uldservice/src/main/resources/application.properties` | **NEW** — port 9097, DB config, Flyway, RabbitMQ, Caffeine cache, JWT secret, Feign URLs |
| `backend/.../uldservice/src/main/resources/db/migration/V1__create_uld_tables.sql` | **NEW** — creates uld, uld_awb, uld_piece, uld_type_config tables with indexes |
| `database/migrations/V39__create_uld_service_tables.sql` | **NEW** — synced from ULD service, creates ULD schema tables |
| `backend/aircargo-uld-service/pom.xml` | **UPDATED** — includes feign-clients, cache, caffeine, openfeign as dependencies |
| `backend/pom.xml` | **UPDATED** — added aircargo-uld-service module |
| `AGENTS.md` | **UPDATED** — Phase 7 status → ✅ Complete |

## Recent session changes (July 28, 2026 — Phases 3-5, 8-12: Full microservices migration completion)

| File | Change |
|------|--------|
| `backend/.../service/FlightServiceImpl.java` | Added `@Transactional(readOnly = true)`, `Sort.by("flightDate").descending()`, paginated overload returning `PageResponse<FlightDTO>` |
| `backend/.../controller/FlightController.java` | `GET /api/flights` now returns `PageResponse<FlightDTO>` when `page`/`size` params present; `GET /api/aircraft-types` endpoint added |
| `backend/.../service/BookingServiceImpl.java` | Added `@Transactional`, `Sort.by("createdAt").descending()`, `findByAirlineId` support, paginated overload with `PageResponse<BookingDTO>` |
| `backend/.../controller/BookingController.java` | `PATCH /{id}/awb` now returns `ResponseEntity<BookingDTO>` (not Void); `GET /api/bookings` supports `airlineId`/`flightId` query params |
| `backend/.../service/MawbServiceImpl.java` | **NEW** — full implementation: getAll with airline/flight/status filters (list + page), create/update/updateStatus, delete, updateSupportingDocs, getSupportingDocsPdf (openhtmltopdf) |
| `backend/.../controller/DuaRecordController.java` | **NEW** — compliance endpoints moved from monolith to mawb-service (`/api/compliance` CRUD + audit logging) |
| `backend/.../service/DuaRecordService.java` | **NEW** — CRUD service for DUA records with `@Transactional` + `@Cacheable` |
| `backend/.../entity/DuaRecord.java` | **NEW** — entity with flat `mawbId` UUID FK (no @ManyToOne) |
| `backend/.../entity/DuaStatus.java` | **NEW** — enum: PENDING, COMPLETED, REJECTED |
| `backend/.../dto/DuaRecordDTO.java` | **NEW** — DTO with fromEntity mapper |
| `backend/.../repository/DuaRecordRepository.java` | **NEW** — findByMawbId, findAllByOrderByCreatedAtDesc, existsByMawbId |
| `backend/.../config/CacheConfig.java` | Added `dua-records` cache |
| `backend/.../config/SecurityConfig.java` | Added `/api/compliance/**` → ADMIN/SUPER_USER |
| `backend/.../resources/db/migration/V2__create_dua_record_table.sql` | **NEW** — creates dua_record table |
| `backend/pom.xml` | Removed `aircargo-api` module; all modules compile without monolith |
| `backend/aircargo-load-planning-service/` | **NEW** — stateless Feign-based service (port 9098): flight plan, close, upload manifest, export manifest, pallet sheets |
| `backend/aircargo-export-service/` | **NEW** — read-only analytics (port 9099): 4 controllers, 11 entities, Swift + Swagger |
| `backend/aircargo-notification-service/` | **NEW** — RabbitMQ listeners (port 9100): receipt.created, booking.confirmed, flight.departed, mawb.status.changed |
| `backend/aircargo-gateway/.../RouteConfig.java` | Removed `compliance-service` and `api-fallback` routes (monolith references); added `/api/compliance/**` → mawb-service; added 9 Swagger API-doc routes per service |
| All `application.properties` | Added `springdoc.api-docs.path` and `springdoc.swagger-ui.path` for OpenAPI/Swagger |
| `k8s/aircargo-api.yml` | **DELETED** — monolith K8s manifest removed |
| `docker/docker-compose.services.yml` | Removed `aircargo-api` service; gateway no longer depends on monolith |
| `backend/aircargo-api/` | **DELETED** — entire monolith module removed |
| `AGENTS.md` | Updated structure, migration phases, migration sources |

## Recent session changes (July 28, 2026 — Full stack running + JDK 25 compat)

| File | Change |
|------|--------|
| `backend/.../gateway/config/SecurityConfig.java` | Changed `.anyExchange().authenticated()` → `.anyExchange().permitAll()` — Spring Security was blocking all requests before `JwtGatewayFilter` could validate JWT |
| `backend/.../booking-service/BookingServiceApplication.java` | Added `@EnableFeignClients(basePackages = "com.aircargo.feign.client")` — `FlightClient` injection was failing |
| `backend/.../booking-service/.../V1__init.sql` | Made idempotent: `CREATE TYPE commodity_type` → `DO $$ ... IF NOT EXISTS` block, `CREATE TABLE` → `CREATE TABLE IF NOT EXISTS`, `CREATE INDEX` → `CREATE INDEX IF NOT EXISTS` |
| `backend/.../mawb-service/.../V1__init.sql` | Made idempotent: wrapped `mawb_status` and `commodity_type` in `DO $$` blocks, `CREATE TABLE`/`INDEX` → `IF NOT EXISTS`; Fixed `origin BPCHAR(3)` → `VARCHAR(3)`, `destination BPCHAR(3)` → `VARCHAR(3)` |
| `backend/.../warehouse-service/.../V1__create_warehouse_tables.sql` | Fixed `destination char(3)` → `varchar(3)`, `origin char(3)` → `varchar(3)` |
| `backend/.../warehouse-service/.../WarehouseReceiptRepository.java` | Added `@Modifying @Query` JPQL to `supersedeAllByMawbId()` methods — were using invalid Spring Data derived query name |
| `backend/.../flight-service/.../V1__init.sql` | Fixed `origin BPCHAR(3)` → `VARCHAR(3)`, `destination BPCHAR(3)` → `VARCHAR(3)` |
| `backend/.../uld-service/.../V1__create_uld_tables.sql` | Fixed `destination BPCHAR(3)` → `VARCHAR(3)` |
| `backend/.../export-service/application.properties` | Fixed `spring.datasource.username` default `aircargo` → `aircargo_user`; `spring.datasource.password` default `aircargo` → `aircargo_pass_2024`; `hikari.read-only=true` → `false` |
| `backend/.../export-service/.../V1__create_export_bi_schema.sql` | Added `CREATE SCHEMA IF NOT EXISTS export_bi;` |
| `backend/.../notification-service/application.properties` | Fixed `spring.rabbitmq.password` default → `RABBITMQ_PASSWORD` env var (value moved to gitignored `.env`) |
| `backend/.../notification-service/.../V1__create_notification_tables.sql` | Added `CREATE SCHEMA IF NOT EXISTS notification;` |
| All 7 service `application.properties` | Added `spring.flyway.table=flyway_schema_history_{service}`, `spring.flyway.baseline-on-migrate=true`, `spring.flyway.baseline-version=0` — custom Flyway tables to avoid checksum conflicts on shared DB |
| All 7 service Application.java | Added `@SpringBootApplication(scanBasePackages = {"com.aircargo.{service}", "com.aircargo.common"})` + `@EntityScan` where needed — required for `JwtAuthFilter`/`JwtUtil` beans and common entities |
| `backend/aircargo-load-planning-service/application.properties` | Added `spring.autoconfigure.exclude=...DataSourceAutoConfiguration,...HibernateJpaAutoConfiguration` — service has no DB but inherits `spring-boot-starter-data-jpa` from common |

## State (July 28, 2026)

**10/10 services UP** (gateway:8080, auth:9092, flight:9093, booking:9094, mawb:9095, warehouse:9096, uld:9097, load-planning:9098, export:9099, notification:9100). End-to-end login → JWT → airlines via gateway works.

**JDK compatibility**: Both JDK 21 and JDK 25 work. JDK 25 emits `WARNING: Restricted methods will be blocked` but does not block execution. Default `java` command on this system is Corretto 25 via SDKMAN.

**Infrastructure**: PostgreSQL (port 5432, user `aircargo_user`, password `aircargo_pass_2024`) and RabbitMQ (port 5672, user `aircargo`, password from `.env`) running via Docker Compose.

## Import paths

Frontend uses `@/` → `./src/` (configured in `vite.config.js`).

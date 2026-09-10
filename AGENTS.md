# Aircargo — agent notes

## Recent session changes (Sep 10, 2026 (2) — Navegación al scrollear: cabeceras sticky + scroll interno en móvil + scroll a detalle/reset de lista)

**Contexto**: pedido del usuario — "mejora la navegacion al hacer scrolling, verifica todo". Diagnóstico por CDP de la cadena de alturas: en móvil la media query global `@media (max-width:640px)` colapsa `.h-screen`→`height:auto` y `.max-h-screen`→`max-height:none`, por lo que `.ds-page` crecía hasta el contenido (page=1053px), `main` scrollaba la página completa y el `.table-scroll-wrapper` (overflow-x:auto) NUNCA generaba scroll vertical → la cabecera `.ds-table-header` (ya sticky dentro del wrapper) no fijaba nada. En desktop `.ds-page` sí era app-shell (`h-screen`), pero `.table-scroll-wrapper` tenía solo `overflow-x` (el eje y computaba `auto` por no poder ser `visible` con el otro eje en auto) y la cabecera igual se perdía al bajar la lista.

| File | Change |
|------|--------|
| `frontend/src/assets/main.css` | **Wrapper**: `.table-scroll-wrapper` ahora declara `overflow-y:auto` explícito + `overscroll-behavior:contain` + `scrollbar-color/width:thin` (Firefox) + scrollbar webkit `height:6px; width:10px` (base y track/thumb de tokens). **Nueva regla**: `.ds-table-section .ds-table-header { position: sticky; top: 0; z-index: 30 }` — la cabecera queda clavada arriba mientras las filas scrollean debajo; sigue haciendo scroll horizontal junto a las filas (está dentro del mismo wrapper, grid-cols iguales, la alineación 860/960 se conserva — verificado). **App-shell en móvil** para grillas puras: nueva clase `.ds-page-grid` (Bookings/Flights) que DENTRO de `@media (max-width:640px)` re-impone `height:100dvh !important; max-height:100dvh !important; overflow:hidden !important` — así esas vistas mantienen el modelo app-shell (cabecera de página fija + tabla con scroll interno + header sticky) en vez de crecer la página. El resto de vistas conserva el scroll natural de página (móvil). |
| `frontend/src/views/BookingsView.vue` | Raíz `ds-page` → **`ds-page ds-page-grid`**; `ref="scrollWrapRef"` en el wrapper (L59) y el watch existente de `store.selectedFlightId` (L1007) ahora hace `scrollWrapRef.value?.scrollTo({ top:0, left:0 })` (cambiar de vuelo reencauza el scroll de la lista al inicio). |
| `frontend/src/views/FlightsView.vue` | Raíz `ds-page` → **`ds-page ds-page-grid`**; `ref="scrollWrapRef"` en wrapper (L39) + import `watch` añadido + **watch nuevo sobre `[searchText, destFilter, dateFrom, dateTo]`** → `scrollTo({top:0,left:0})` (filtrar reinicia el scroll de la lista). |
| `frontend/src/views/UldsView.vue` | `ref="detailPanel"` en el `.ds-split-detail` (L44) y **`toggleUldExpansion`** (L1164): al ABRIR un ULD con `window.innerWidth < 1024` hace `rAF(() => detailPanel.value?.scrollIntoView({ behavior:'smooth', block:'start' }))` → en móvil (formulario apilado arriba, listado abajo) al tocar una tarjeta se navega al detalle. Desktop intacto (layout lateral). |

**Decisiones de diseño**: (1) el sticky global solo afecta donde hay `ds-table-section` DENTRO de un scroller vertical (Bookings/Flights); en Ulds/Receipts/Exports la cabecera ya vivía fuera del scroller (sticky es no-op) y MawbsView usa su thead sticky + columnas sticky propias — por eso el selector es `.ds-table-section .ds-table-header` y no un override amplio; (2) `.ds-page-grid` es la excepción por-vista al colapso móvil global (`.h-screen`→auto es conveniente para formularios largos como Receipts/Security, no para grillas de datos); (3) `100dvh` (no 100vh) para que la barra de URL móvil no tapan el borde inferior.

**Verificación determinista (CDP, harness `/tmp/opencode/cdp-scroll.mjs` y `/tmp/opencode/cdp-heights.mjs`)**: móvil (innerWidth 500): `.ds-page` = 725px exacto (innerHeight, ya no 1053), wrapper acotado (h≈413, `scrollHeight` 2701 tras inyectar 40 filas), `wrapper.scrollTop=350` SE mantiene, `scrollLeft=120` y `headerTop` permanece 0 → cabecera pinned con filas scrolleando debajo; idéntico en `/flights` (sticky:sticky, z:30, wrapOverflowY:auto). Alineación horizontal intacta: `/bookings` header=860==list==row (hasHScroll, wrapClientW 470/scrollWidth 860) y `/flights` 960 (probe `/tmp/opencode/cdp-check.mjs`). Antes del fix: wrapper `h==scrollHeight (2701)` sin scroll (scrollTop se quedaba en 0) y sticky inoperante. UldsView: `check:refs` confirma refs enlazadas. Limpiar perfiles `/tmp/opencode/cdp-prof*` tras usar y `pkill -f '[g]oogle-chrome.*remote-debugging-port=9222'`.

**Checks**: `npm run lint` ✓ (check-sfc-refs 43 SFC 0 no resueltas, LINT_EXIT=0), `vitest` **18/18** ✓, `npm run build` ✓ (2.45s). Sin commits.

## Recent session changes (Sep 10, 2026 — Fix responsive móvil: grillas alineadas en celular ~375px + zoom habilitado)

**Contexto**: pedido del usuario — "la visualización es pésima, no es responsive en celular; ajusta o recrea lo necesario". Diagnóstico del contenedor `.ds-table-section` (flex-col): las cabeceras `.ds-table-header` (grid-cols-12, `min-width` inline) y las filas `.ds-table-row` (grid-cols-12, sin min-width) se aplastaban a ~375px y quedaban desalineadas. Solo 3 vistas de datos estaban rotas: Flights (header 960px), Bookings (860px), Ulds (750px). El resto ya era correcto: Dashboard/Exports/Users/Settings/Mawbs + modales usan `<table>` real con min-width; LoadPlanning usa `.lp-grid` con min-width 960 en clase; WarehouseReceipts ya alinea header 1000px con filas `min-width:1000px` inline + overflow-x por fila.

| File | Change |
|------|--------|
| `frontend/index.html` | `<meta name="viewport">` con `maximum-scale=1` (bloqueaba pinch-zoom) → **`viewport-fit=cover`**; ahora se puede hacer zoom en móvil |
| `frontend/src/views/BookingsView.vue` L152 | Contenedor de filas (`.divide-y ... overflow-y-auto flex-1 min-h-0`) con `style="min-width: 860px"` = al min-width del header |
| `frontend/src/views/FlightsView.vue` L61 | Ídem con `min-width: 960px` (= header) |
| `frontend/src/views/UldsView.vue` L114 | Ídem sobre `.divide-y divide-slate-100 max-h-[240px] overflow-y-auto scrollbar-none` con `min-width: 750px` (= header grid-cols-13) |
| `frontend/src/assets/main.css` L628-637 | **FIX reroute móvil (2º reporte "la vista muy cortada")**: el drawer móvil del Sidebar (`.app-layout > aside` en `<768px`) usaba `width: min(260px, 86vw)` y solo se ocultaba con `-translate-x-full` → seguía **ocupando 260px en el flex layout** aunque estuviera fuera de pantalla y el `main` quedaba con el remanente (~240px de 500; ~115px en un 375px real). Ahora en `<768px` el aside es **`position:fixed; top:0; bottom:0; left:0; z-index:50`** (overlay drawer, fuera del flujo) → el contenido se redistribuye a ancho completo con el drawer cerrado y el drawer se superpone con el backdrop (z-40) al abrir. `!important` necesario porque la clase scoped `.relative[data-v]` (0,2,0) vence a `.app-layout > aside` (0,1,1) |

**Decisiones**: (1) `min-width` inline por vista en el contenedor de filas (no un override global con `!important`: el CSS móvil existente en main.css L629-654 ya maneja el layout responsive, solo faltaba la alineación interna); (2) el wrapper `.table-scroll-wrapper` (`overflow-x:auto`, main.css L613) desplaza cabecera+filas juntas porque `.ds-table-header`/`.ds-table-row` son grid-cols-12 sin `gap` → al mismo min-width quedan pixel-perfect alineadas y con scroll horizontal único; (3) el drawer móvil se saca del flujo con `position:fixed` (no width:0: conserva la animación translate y el ancho real al abrir).

**Verificación determinista (no visual)**: el modelo actual no puede leer imágenes, así que se verificó por CDP (Node 22, WebSocket global, sin deps) con Chrome headless + token JWT HS512 minted (JWT_SECRET del `.env` local, user admin@aircargo.com, site SDQ) y cookies httpOnly `aircargo_at`/`aircargo_rt` + `localStorage.aircargo_auth` (profile+selectedSiteId). `/bookings` → header 860 == list 860 == row 860, wrapper `clientWidth=210 / scrollWidth=860` → scroll horizontal activo; `/flights` → 960==960==960 ídem. `/ulds` con BD sin ULDs muestra EmptyState (la tabla solo se renderiza con datos; fix determinista). **Layout del drawer**: antes → aside `w260` reservando flujo y contentCol `left:260 w:240` en innerW=500; después del fix → aside fixed fuera de flujo y contentCol/main/header **full-width 500** con drawer cerrado, y al forzar `translate-x-0` el drawer se superpone (left 0..260) sin empujar el contenido. Harness en `/tmp/opencode/cdp-check.mjs` y `/tmp/opencode/cdp-layout.mjs` (borrar perfiles `/tmp/opencode/cdp-prof*` tras usar; matar chrome sobrante con `pkill -f '[g]oogle-chrome.*remote-debugging-port=9222'`). Checks: `check:refs` 43 SFC ✓, `lint` ✓, `vitest` 18/18 ✓, `npm run build` ✓. Desktop intacto (media query `<768px` solo). Commits: `0bd2776` (grillas + zoom) y fix drawer (ver más abajo).

## Recent session changes (Sep 9, 2026 — Deploy a AWS EC2: Version1.3 completa con los 10 servicios)

**Contexto**: pedido del usuario — "Necesito que este aplicativo sea el que se corra en la instancia de AWS EC2, ya te había pasado el pem y demás datos, actualiza". Se desplegó la versión local completa (`b1ca0e8`) en la instancia de producción real. El usuario eligió **"Commit + push + deploy completo"** (autorización explícita de commit/push, excepción a la convención "sin commits").

**Topología real de producción (NO es Docker/K8s)**: la instancia `ec2-3-23-79-76.us-east-2.compute.amazonaws.com` (hostname `ip-172-31-26-236`, usuario `ubuntu`, t2-small ~2 GB RAM + 4 GB swap) corre el stack como **jars nativos Java 21** en `~/aircargo-saas/backend/*/target/` + **nginx del sistema**:80 sirviendo `~/aircargo-saas/frontend/dist` (proxy `/api/` → localhost:8080, `client_max_body_size 20m`) + Postgres :5432 + RabbitMQ :5672. App dir activo: `~/aircargo-saas` (git clone de Version1.3; existen `aircargo-saas-Version1.2` y `.bak`). Acceso: `ssh -i ~/aircargo-testing.pem ubuntu@ec2-3-23-79-76.us-east-2.compute.amazonaws.com`. Antes del deploy solo corrían 6 servicios (auth/flight/booking/mawb/warehouse/gateway) con jars de Aug 31 en commit `d9712b9` — faltaban uld/load-planning/export/notification.

| Paso | Detalle |
|------|---------|
| Commit local | `b1ca0e8` "chore: blindaje camaras + rate-limit anonimo 600/min + paleta acento + fixes warehouse/set-password" (23 archivos, +601/−152, crea `frontend/src/utils/accent.js`); verificado sin secretos (`.env` gitignored) y pijada doble: `git diff` filtrando `.env`/`.pem` |
| Push | `06dfeac..b1ca0e8` a `origin/main`; remoto en `b1ca0e8aa0e52c8741dd71edb5169820938307cf` |
| EC2 git pull | `d9712b9..b1ca0e8`; trajo `useLiveRefresh.js`, `mdi.js`, `accent.js`, `density.js`, `font.js`, `mdiIcon.js`, `ReceiptFormLayout.spec.js`, `scripts/load-test.sh`; untracked: `.env.bak-20260830-221244`, `start-13-nobuild.sh`, `start-13.sh` |
| Backend rebuild | `rm -f */target/*.jar` + `nohup mvn -o install -DskipTests -q` (offline, PID 2381705) → 12 módulos instalados en `~/.m2/repository/com/aircargo/*/1.2.0-SNAPSHOT/` (timestamps Sep 9 04:09-10) y fat jars regenerados |
| Frontend build | Lección aplicada: **NUNCA lanzar `npm run build` mientras `npm install` corre** — el `@mdi/js` a medio instalar hizo fallar 2 builds con `[vite]: Rolldown failed to resolve import "@mdi/js"` (log `/tmp/fe-build.log`). El `npm install` inicial había muerto sin log y se relanzó en **primer plano**: "added 1 package, removed 70 packages" (el node_modules previo quedó podrido por el install interrumpido). Build final: **✓ built in 5.94s** con `dist/assets/zbar-*.wasm` presente (scan ZBar OK). El `pgrep -f "npm install"` daba self-match del propio comando → falso "INSTALLING"; ver procesos reales con `ps`.
| Arranque | `nohup ./start-13-nobuild.sh > /tmp/start-all.log 2>&1 &` → **✅ All 10 services healthy** (auth 22s, gateway 9s, flight/booking/mawb/warehouse 21-22s, uld 24s, load-planning 18s, export 21s, notification 25s). El script usa `-Dspring.flyway.validate-on-migrate=false` (DIRTY_VALIDATE) y `-Dspring.datasource.hikari.maximum-pool-size=3` (RAM limitada). |
| Salud final | `/actuator/health` **UP** en 8080 + 9092-9100; login smoke por gateway → `401 {"error":"Email y/o contraseña incorrectos"}` (mensaje genérico CQRS, no expone usuarios) y `GET /api/flights/list` anónimo → 401 (auth exigida). Errores en logs de hoy: **solo 5** en notification-service (colas RabbitMQ pre-borrado, ver abajo). RAM: 1783/1906 MB usada + 2262 MB swap (límite, esperado en t2-small con 10 JVM 256 MB Xmx c/u). |
| **FIX colas RabbitMQ** | `aircargo.notifications` existía SIN DLX → notification-service fallaba con `PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange' for queue 'aircargo.notifications'` en cada arranque (documentado en AGENTS.md sesión Aug 25). Fix: `sudo rabbitmqctl delete_queue aircargo.notifications` → el servicio la redeclaró automáticamente con `{"x-dead-letter-exchange","aircargo.dlx"}` + DLQ `aircargo.notifications.dlq` y 1 consumidor. Sin mensajes perdidos (0 ready). Las colas `aircargo.loadplanning.invalidate` y `aircargo.uld.mawb-sync` seguían correctas (1 consumidor c/u). |

**Estado final**: los 10 servicios UP apuntando a la BD real de EC2 (Postgres del sistema), frontend `dist/` del Sep 9 04:28 servido por nginx :80 (`assets/index-BtMpCISX.js`, `assets/zbar-*.wasm`), rate-limit anónimo 600/min activo. URL pública: **`http://ec2-3-23-79-76.us-east-2.compute.amazonaws.com`**. Para re-desplegar en EC2: `ssh …` → `git -C ~/aircargo-saas pull` → `rm -f */target/*.jar` → `mvn -o install -DskipTests` → `cd frontend && npm install && npm run build` → `nohup ./start-13-nobuild.sh`. Pendiente para próximas sesiones producción: el stack corre sin HTTPS (nginx :80 solo) y sin systemd/supervisord (los procesos se lanzan con nohup; un reboot de la instancia requiere re-arranque manual).

## Recent session changes (Sep 8, 2026 (5) — Captura por cámara del dispositivo blindada (no debe fallar))

**Contexto**: pedido del usuario — "la captura de imagen por vía a la cámara del dispositivo no debe fallar. Ajusta lo necesario." Auditoría de las 3 superficies de cámara: `CameraCapture.vue` (foto de pieza en recepción, instanciado 2× en `WarehouseReceiptsView`: fotos de evidencia del recibo + evidencia del MAWB) y `ScanPanel.vue` (escáner de códigos con `BarcodeDetector` nativo + fallback ZBar WASM). Se encontraron 4 fallos reales que podían producir imagen vacía/colgada y se blindó el flujo completo.

| File | Change |
|------|--------|
| `frontend/src/components/CameraCapture.vue` | **Fix A (imagen vacía)**: `capture()` ahora exige `video.readyState >= 2` + `videoWidth/videoHeight` reales ANTES de dibujar al canvas — antes, si el usuario pulsaba Capturar antes de que el stream decodificara el primer frame, el canvas era 0×0 y `toDataURL` producía imagen inutilizable. Guard adicional `dataUrl === 'data:,'` y todo el cuerpo en try/catch con mensaje de error recuperable (antes un throw de `drawImage` dejaba el canvas muerto sin feedback). **Fix B (resolución)**: `ideal: 1280×720` para fotos de evidencia. **Fix C (errores legibles)**: detección de contexto sin `getUserMedia` (HTTP no-localhost / navegador sin cámara) y mapeo de `NotAllowed/SecurityError`, `NotFound/DevicesNotFound`, `NotReadable` a mensajes concretos + **botón "↻ Reintentar"** que re-arranca `startCamera()` (antes el usuario quedaba atascado sin opción). **Fix D (stream colgado)**: si el modal se cierra mientras `getUserMedia` resuelve → `stream.getTracks().stop()` y return (luz de cámara encendida sin UI). `watch(show)` usa `await nextTick()` en vez de `setTimeout(100)` (montaje garantizado antes de abrir cámara) y `muted` en `<video>` para iOS |
| `frontend/src/components/ScanPanel.vue` | **Fix A (error no ciego)**: en error de cámara ya NO cierra el modal (`closeCamera()` era abrupto y sin feedback dentro del modal); ahora queda overlay `cameraError` a pantalla completa del modal con **botón Retry** (`retryCamera()` → `stopStream()` + `openCamera()`). **Fix B (frame listo)**: helper `waitForFrame(video, 5s)` — los detectores no arrancan hasta que haya un frame real; timeout 5s → error claro, no GPS infinito. **Fix C (de-dupe)**: `shouldScan`/`markScanned` con ventana de 3s por código — antes, mientras el código seguía enfrente, los intervalos de detección (250/400ms) re-disparaban `processScan` una y otra vez (piezas duplicadas). **Fix D (race)**: si el modal se cierra durante el await de `getUserMedia` → `stopStream()`; guards `!video.videoWidth` en ambos loops; fallo ZBar `index out of bounds` se ignora silenciosamente |
| Verificación | `check:refs` ✓ 43 SFC 0 refs, `lint` ✓ 0 errores (1 warning transitorio de `formats` no usado → variable eliminada), `vitest` **18/18** ✓, `npm run build` ✓ (2.70s). Sin commits |

**Decisiones de diseño**: los errores de cámara se muestran en el modal (contexto recuperable: Reintentar/Cancelar), nunca cierran la ventana; el patrón de stream-limpieza es idempotente (`stopStream()` a prueba de null) y se reutiliza entre open/close/retry/unmount; los mensajes distinguen permiso denegado / cámara ausente / cámara en uso / backend-getUserMedia ausente para que el operador actúe con precisión. Nota de entorno: `getUserMedia` solo existe en contextos seguros (HTTPS o `localhost`); en dev sobre red la UI ahora lo dice explícitamente.

## Recent session changes (Sep 8, 2026 (4) — Fix spam de warnings de iconos + 429 de login por rate limit anónimo)

**Contexto**: el login con `emmanuel...@gmail.com` arrojaba (a) decenas de warnings `Vue received a Component that was made a reactive object... MdiIcon` y (b) `POST /auth/login → 429 Rate limit exceeded` tras unos pocos intentos.

**Causas raíz**: (1) `useIcons.js` guardaba el mapa de iconos (objetos-componente) en un `ref({})` → Vue hacía reactiva en profundidad cada componente de icono → warning por cada `:is="icons.X"` en cada render (`MdiIcon` era el que más se veía por el adaptador de `@/mdi`). (2) El rate limiter anónimo del gateway era un **hardcode de 10/min por IP** en `/auth/login` y `/auth/refresh`; el flujo de enrolamiento MFA (login → 428 → re-login) más re-intentos agotaba el cupo en menos de un minuto y bloqueaba el login de un usuario real con 429.

**Escala multi-usuario (>100 simultáneos)**: el límite anónimo es **por IP**, no por usuario (no hay sesión aún en login/refresh). Detrás de una sola IP (NAT de oficina, VPN, o el LB/ingress del servidor) TODOS los usuarios comparten el mismo bucket → si el límite es bajo (10-30/min), el usuario 11+ recibe 429 garantizado. El brute-force real lo defiende el auth-service (lockout 5 intentos/30 min por cuenta, `LoginCommandHandler`), así que el guard por-IP del gateway debe ser **generoso y configurable**, no bloqueador. Default ahora **600/min por IP** (igual al por-usuario).

| File | Change |
|------|--------|
| `frontend/src/composables/useIcons.js` | `ref({})` → **`shallowRef({})`** — el mapa de iconos se reemplaza completo (`current.value = map`), nunca se muta en profundidad; así los componentes de icono NO se envuelven en proxies reactivos → desaparecen los Warnings "Component that was made reactive" (el `computed` que devuelve `useIcons()` sigue igual: solo lee `.value`, shallowRef es suficiente) |
| `backend/.../gateway/filter/RateLimitFilter.java` | Constante `ANONYMOUS_LIMIT_PER_MINUTE = 10` eliminada → **`@Value("${app.gateway.rate-limit.anonymous-limit-per-minute:600}")`** (`anonymousLimitPerMinute`), usado tanto en `anonymousConfig()` (in-memory) como en `checkRedisAnonymous()` (Redis); default **600** (min 10 previo → 30 → 600 para no bloquear >100 usuarios tras una misma IP) |
| `backend/.../gateway/application.properties` | Nueva var `app.gateway.rate-limit.anonymous-limit-per-minute=${RATE_LIMIT_ANONYMOUS_PER_MINUTE:600}` + comentario extendido explicando la escala multi-usuario (una sola secuencia MFA login→428→re-login ya consume 2-3 llamadas anónimas; detrás de NAT/LB comparten IP) |

**Verificación**: `mvn -o compile -pl aircargo-gateway -am` EXIT=0; frontend `check:refs` ✓ 43 SFC, `lint` ✓, `vitest` **18/18** ✓. Sin commits. OJO: el gateway en marcha aún corre el jar viejo (10/min); reconstruir y reiniciar el gateway para aplicar el nuevo límite.

**Contexto**: bug reportado por el usuario — un usuario que necesita reset de contraseña y/o MFA, al intentar cambiar la contraseña vía `/set-password` recibía error y "no aceptaba la nueva contraseña". **Causa raíz (frontend)**: el backend (auth-service) guardaba el hash de la contraseña ANTES de chequear la política MFA, y si `app.mfa.mandatory=true` + política exige (re)enrolamiento (`required`/`reset`/`expired`) respondía **428 PRECONDITION_REQUIRED** con `{mfaEnrollmentRequired:true, enrollToken, email, mfaReason, message}` SIN emitir sesión — tanto en `POST /set-password-token` (AuthController L221-257) como en `POST /set-password` (SetPasswordCommandHandler L68-101). `SetPasswordView.handleSetPassword` solo mapeaba 404/403/401; cualquier otro status caía en `login.error.generic` → el usuario veía error, creía que la contraseña no se aceptó. La contraseña SÍ estaba guardada.

**Decisión de UX (pedido del usuario: "que sea fácil para los usuarios")**: cambiar la contraseña NO debe implicar enrolar MFA en el mismo flujo (QR + secret + código dentro del set-password es fricción innecesaria). El 428 mfaEnrollmentRequired se trata como **éxito** (la contraseña ya quedó guardada); el usuario ve el mensaje de confirmación y es llevado al login, donde el flujo de enrolamiento MFA ya está guiado y probado (LoginView maneja el 428 con banner/QR/re-login). El backend NO emite sesión en ese caso, así que nadie opera sin MFA (política intacta).

| File | Change |
|------|--------|
| `frontend/src/views/SetPasswordView.vue` | **Reescrito SIMPLE** (sin flujo MFA inline): formulario único (email readonly si no hay token, currentPassword si aplica, nueva+confirmación, reglas de fuerza). `handleSetPassword`: en `catch`, si `status === 428 && data?.mfaEnrollmentRequired` → **`successMsg = t('setPassword.success')` + `router.push('/login')`** (el MFA se configura al iniciar sesión); 404/403/401 → errores específicos; resto → `login.error.generic`. Sin `useAuthStore`, sin `step`, sin `mfaEnrollSetup`/`mfaEnrollEnable`, sin QR, sin site-select — se eliminan los imports y refs correspondientes |

**Verificación**: `check:refs` ✓ 43 SFC 0 refs, `lint` ✓ 0 errores, `vitest` **18/18** ✓, `npm run build` ✓ (2.62s). Backend sin cambios (el 428 del backend es correcto; el login ya lo convierte en enrolamiento guiado). Sin commits.

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
| `k8s/` | Kubernetes manifests (legacy) | Full K8s deployment for all services. **`secret.yml` NO vive aquí** — está en `~/Desktop/Projects/Rannik/aircargo-deploy-secrets/secret.yml` (fuera del repo, gitignore lo bloquea). Para desplegar: `kubectl apply -f k8s/ -f ../aircargo-deploy-secrets/secret.yml` (el Secret se llama `aircargo-secrets` y los manifests lo referencian por nombre, no por ruta) |
| `deploy/` | **Production K8s (kustomize)** | `deploy/k8s/base/` — base manifests (namespace, configmap, secrets template, postgres, rabbitmq, 11 services, frontend, ingress, networkpolicies). `deploy/k8s/overlays/{staging,production}/` — environment-specific overrides. `deploy/deploy.sh` — deployment script. `deploy/generate-secrets.sh` — secret generator. `deploy/PRODUCTION_DEPLOYMENT.md` — full guide. |

## Production Deployment

```sh
# Generate secrets (review before applying!)
./deploy/generate-secrets.sh | kubectl apply -f -

# Deploy to staging
./deploy/deploy.sh staging

# Deploy to production (requires confirmation)
./deploy/deploy.sh production

# CI/CD: GitHub Actions workflow at .github/workflows/ci-cd.yml
# Triggers: push to develop → staging; push tag v* → production
```

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

## Recent session changes (Sep 8, 2026 (2) — Auditoría del proceso de recibo: fixes en pipeline y contrato legacy)

**Contexto**: pedido de auditar el proceso completo de recibo (backend `aircargo-warehouse-service` + frontend) y validar que todo funcione. Se leyó el flujo completo: `WarehouseController` (`/api/warehouse/receipts/emit`, `PUT /{id}`), `WarehouseReceiptController` (`/api/receipts`), `WarehouseServiceImpl` (emit/update/validate + artefactos), `ReceiptFullPdfService`, `ReceiptExportService`, `WarehouseReceiptServiceImpl`, entidad y V1 DDL, y el contrato frontend (`stores/app.js`, `EditReceiptModal.vue`, `api/receipts.js`). **El frontend NO usa los endpoints legacy de `/api/receipts` POST/PUT/DELETE** (usa `POST /emit` + `PUT /api/warehouse/receipts/{id}`); los corrige de todos modos porque son superficie API viva.

| File | Change |
|------|--------|
| `backend/.../warehouseservice/service/WarehouseServiceImpl.java` | **Fix C (user-visible)**: `updateReceipt` ahora hace `existing.setPdfData(null); existing.setExcelData(null);` **antes** del `save`/regeneración de artefactos — antes, `generateReceiptPdf` (ReceiptFullPdfService L46-49) hacía short-circuit si `pdfData` ya existía → tras editar un recibo el PDF descargado seguía mostrando los datos viejos mientras el Excel se regeneraba siempre. **Fix B**: el catch vacío de `generatePersistedArtifacts` (`// Log error`) ahora hace `log.error("Failed to generate persisted artifacts ...", e)` real. **Fix D**: `emitReceipt` deja de duplicar la lógica de supersede inline y usa el helper `supersedeExistingReceipts(mawbId, newReceiptId)` (era código muerto) |
| `backend/.../warehouseservice/controller/WarehouseReceiptController.java` | **Fix A**: `POST /api/receipts` ya NO usa `receiptService.save(dto)` (guardado plano que omitía pieces/totals/supersede/artefactos/sync) — ahora delega en `warehouseService.emitReceipt(dto, principal, request)`. `PUT /api/receipts/{id}` delega en `warehouseService.updateReceipt(id, dto, principal, request)` (antes `save(dto)` tras un getById). Auditoría RECEIPT_CREATE/RECEIPT_UPDATE conservada |
| `backend/.../warehouseservice/service/WarehouseReceiptServiceImpl.java` | **Fix cache**: `save()` y `delete()` ahora evictan `{"warehouse-receipts", "receipt-pieces"}` (antes solo `warehouse-receipts`; `getPieces` cachea en `"receipt-pieces"` → un delete dejaba piezas obsoletas en caché bajo la misma clave default) |
| Backend verificado (sin cambios) | **Bug E documentado, NO corregido (decisión deliberada)**: la entidad `WarehouseReceipt.mawbId` es `@Column(name="mawb_id")` (nullable) mientras el DDL V1 la declara `mawb_id uuid NOT NULL`. El código guarda `if (saved.getMawbId() != null)` en supersede/sync/status. No se relaja el DDL (un recibo sin MAWB no tiene sentido de negocio y el supersede depende de él); se documenta la divergencia entidad↔esquema (deliberada: `ddl-auto=validate` no la detecta porque valida tipos, no nullability). Cualquier consumidor API que POSTee `/api/receipts` sin `mawbId` seguirá obteniendo 500 DataIntegrityViolation (comportamiento correcto) |

**Verificación**: `mvn -o test -pl aircargo-warehouse-service -am` con `JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto` (el Maven flatpak trae JDK viejo → "release version 21 not supported") → **BUILD SUCCESS**, `WarehouseReceiptServiceImplTest` **5/5** (reactor common+feign+warehouse). Frontend: `npm run check:refs` ✓ 43 SFC 0 refs, `eslint` 0 errores ✓, `vitest` **18/18** ✓, `npm run build` ✓ (2.58s). Sin commits. NOTA de entorno: `MAVEN_BIN` no está en PATH de shells no interactivos → usar la ruta flatpak completa o exportarla explícitamente antes de `mvn`.

## Recent session changes (Sep 8, 2026 — Filtro de vuelo con fecha + "No recibidas"/"No despachadas" + paleta de color de acento)

**Contexto**: pedido sobre la vista de Recepción (WarehouseReceipts): (1) el filtro de vuelo solo distinguía vuelos por número — ahora muestra también la **fecha**; (2) faltaban filtros operativos "MAWB sin recibo" y "MAWB sin despacho"; (3) el usuario pidió **[ajustar y probar a detalle] la paleta de colores** — se implementó un selector de **color de acento** global junto al toggle de tema en el header.

| File | Change |
|------|--------|
| `frontend/src/views/WarehouseReceiptsView.vue` | **Filtro de vuelo con fecha** (template): option ahora muestra `{aerolínea}-{vuelo} ({origin}→{dest}) · {fecha}` vía `fmtFlightDate(iso)` (Intl, locale es-DO/en-US según `t('common.monthsShort[0]')==='Jan'`, fallback a `iso`). **Popup de estado**: `extraStatusOptions` computed con `__NOT_RECEIVED` (`warehouse.notReceived`, dot `bg-slate-300 border-slate-400`) y `__NOT_DISPATCHED` (`warehouse.notDispatched`, dot `bg-white border-blue-400`), renderizados sobre un divisor antes de `statusOptions`; `statusDotClass` extendido con ambos keys; `filteredMawbs` (L1341-1347) mapea `__NOT_RECEIVED`→`!isMawbReceived(m)` y `__NOT_DISPATCHED`→`!isMawbDispatched(m)`. **Helpers** (tras `airlineCodeById`, ~L1025): `fmtFlightDate`, `normAwbCode` (upper + quita espacios/guiones/underscores/slashes, cubre labels `40605857585` vs `406-05857585`), `isMawbReceived` (algún receipt con `r.mawb?.id || r.mawbId === m.id` — receipts traen `mawb` anidado), `isMawbDispatched` (algún `uldAwbs` con `ua.mawbId === m.id` o `normAwbCode(ua.mawbLabel) === normAwbCode(m.awbNumber)`). **Carga**: `onMounted` ahora `await Promise.all([store.loadUldAwbs(), store.loadAllMawbs()])`; `useLiveRefresh` añade `store.loadUldAwbs({ silent: true })` (el store ignora el arg, inofensivo) |
| `frontend/src/utils/accent.js` | **NEW** — paletas: `ACCENTS` (blue/indigo/violet/emerald/teal/rose/amber/slate con accent/strong/violet/cyan), helpers hex→rgba, `getAccent`/`setAccent`/`applyAccent`/`initAccent`, persistencia localStorage **`aircargo_accent`**. Aplica como inline styles en `document.documentElement` (ganan sobre `:root` y tokyo) solo 6 tokens: `--accent`, `--accent-strong`, `--accent-soft`, `--accent-soft-strong`, `--accent-violet`, `--accent-cyan`. `setAccent(null)` limpia → default |
| `frontend/src/assets/main.css` | **Tokens derivados con `color-mix`** para que la paleta propague a TODO: `--shadow-glow`, `--gradient-accent`, `--gradient-title` (light `:root` y bloque tokyo) derivan de `var(--accent)`/`var(--accent-violet)`/`var(--accent-cyan)`; todos los literales `rgba(62,123,250,*)` light (ds-page radial L178, ds-card:hover L194, ds-input focus L344, ds-tab-active L408, ds-spinner L453, shadow-lift L490) y tokyo (`rgba(122,162,247,*)` / `rgba(167,139,250,*)` en ds-page x2, ds-card:hover, ds-input:focus, ds-tab:hover, ds-tab-active::after, ds-spinner, shadow-lift) → `color-mix(in srgb, var(--accent) X%, transparent)`. JS así solo toca 6 tokens y todo el DS (header, botones, tabs, spinners, glows, aurora) reacciona |
| `frontend/src/App.vue` | `initAccent()` importado y llamado junto a `initTheme()`/`initFont()`/`initDensity()` |
| `frontend/src/components/layout/Header.vue` | **Botón swatch de acento** junto al toggle de tema (L~47): 18px cuadrado con el `--accent` actual (o CSS var si default). **Popup con `Teleport to="body"` + `position:fixed`** (z-[100], coords `top/right` calculadas con `getBoundingClientRect` del botón en open y en resize): el `<header>` tiene `overflow-hidden` para las capas decorativas, por lo que un popup `absolute` dentro quedaba **recortado e invisible** (bug reportado por el usuario) — Teleport lo saca del contexto de clipping. Panel `#0f172a` con borde/viñeta profundos, grid 4×2 de swatches con ✓ sobre el activo y enlace **Auto** (reset). Cierra con click-fuera (`@click.stop` en botón y popup + listener de documento) y con **Escape** (`onKeydown`). Funciones `openAccentPop`/`pickAccent`/`resetAccent` |
| `frontend/src/i18n/es.js` + `en.js` | Keys `header.accentHint`/`accentTitle`/`accentAuto` (es/es/en/en) + `warehouse.notReceived` ('No recibidas'/'Not received') y `warehouse.notDispatched` ('No despachadas'/'Not dispatched') |

**Decisiones de diseño**: (1) derivar glow/gradientes/aurora en CSS vía `color-mix` en vez de sobrescribir 15+ tokens desde JS — el JS solo toca 6 tokens y el restante del design system reacciona solo; así la paleta funciona en light y tokyo (los valores tokyo del gradient-title conservan su arranque claro `#f8fafc`). (2) El `__NOT_RECEIVED`/`__NOT_DISPATCHED` son claves pseudo-status locales (nunca viajan al backend; `filteredMawbs` es cliente). (3) El match de despacho normaliza AWB en ambos lados para cubrir labels históricos con formatos mixtos (misma lección del fix de commodity en UldsView). (4) `loadUldAwbs` no acepta `{silent}` pero el argumento extra es ignorado — sin error.

**Verificación**: `check:refs` **43 SFC 0 refs sin resolver** ✓, `eslint` **0 errores** ✓, `vitest` **18/18** ✓, `npm run build` ✓ (4.05s). Budget del changelog: todas las claves i18n nuevas existen en es y en (pantalla del dashboard de filtros OK). Sin commits.

## Recent session changes (Sep 6, 2026 — Vista en blanco al cambiar de vista: FIN + refresh en vivo en Users/Exports)

**Contexto**: el usuario reportó que al cambiar entre vistas quedaba "en blanco" de forma intermitente. Diagnóstico con Chrome DevTools Protocol (headless, Node 22) reprodujo el bug: en algunos cambios la app quedaba con `<main>` vacío **permanente** (sin error en consola, sin fallback del ErrorBoundary). **Causa raíz**: `<transition name="page" mode="out-in">` en App.vue — el montaje del componente nuevo (enter) espera a que termine el leave; nunca termina cuando `prefers-reduced-motion` está activo (con `transition:none` no se dispara `transitionend`) o al cambiar de vista rápido (leave interrumpido a mitad). Resultado: `<main>` con 0 hijos para siempre.

| File | Change |
|------|--------|
| `frontend/src/App.vue` | **FIX** — `<transition name="page" mode="out-in">` → `<transition name="page">` (modo simultáneo: el enter no espera al leave, no puede quedarse colgado). `<main>` gana `relative` para el solape en capas |
| `frontend/src/assets/main.css` | `.page-enter-active, .page-leave-active { position: absolute; top:0; left:0; right:0 }` — con modo simultáneo ambas vistas viven a la vez un instante; el absolute las superpone en su sitio (crossfade sin salto vertical) |
| `frontend/vite.config.js` | **FIX dev-server** — proxy `/api` → `/api/` (prefijo de Vite hace match por prefijo: `/api-catalog`, una ruta SPA, era capturada por el proxy y el hard-load devolvía la Whitelabel del gateway). SPA navigation funcionaba; el hard-load/refresh de `/api-catalog` la rompía |
| `frontend/src/views/{UsersView,ExportsView}.vue` | **Refresh en vivo** (deliverable previo de la sesión): auto-refresh 30s vía `useLiveRefresh` con fallos silenciosos quedan activos |

**Verificación** (E2E headless CDP, token HS512 minted con `JWT_SECRET` real, SUPER_USER + sitio SDQ): pre-fix, hammer de cambios rápidos (200 ms) terminaba en `/ulds` en blanco permanente (0 hijos en main durante 20 s); el mismo hammer post-fix deja todas las vistas renderizando (t50-t20s); pasada con `prefers-reduced-motion: reduce` también verde (antes reproducía el bug hasta en un click normal). Sonda de capas: a t+31ms main tiene 2 hijos superpuestos (vieja absoluta saliendo + nueva en flujo), a t+200ms queda 1 — nunca vacío. Suite completa de rutas (12 + `/`) en hard-load y SPA: `hasMain=true` en todas, incl. `/api-catalog` (ahora 1995 chars, ya no Whitelabel). Checks: `check:refs` 43 SFC 0 ✅, eslint 0 ✅, vitest **18/18** ✅, `npm run build` ✅ (2.77s). Sin commits.

**Lecciones de la sesión**: (1) una transición `out-in` sobre el `<router-view>` principal puede dejar la UI en blanco SIN excepción — el enter simplemente nunca se monta; debuggear con CDP contando hijos de `<main>` durante la transición (`Runtime.evaluate`), no solo tras esperar; (2) `prefers-reduced-motion: reduce` es un trigger real de esta clase de bug porque `transition:none` suprime el `transitionend` que `out-in` espera; (3) el prefijo de proxy de Vite es match por prefijo literal (`/api` captura `/api-catalog`, `/api/...`, `/apiwhatever`); usar `/api/` para rutas API reales cuando existan rutas SPA que empiecen por el mismo token. Scripts de repro en `/tmp/opencode/` (blank-repro.mjs, hammer.mjs, layer-probe.mjs, cat-probe.mjs) — Chrome CDP con `--remote-allow-origins=*` y `pkill -f` con brackets.

## Recent session changes (Sep 5, 2026 (7) — Operadores por (ULD, vuelo): snapshot por vuelo + obligatoriedad)

**Contexto**: pedido de negocio: un ULD asignado a un vuelo debe registrar **operadores por vuelo** (`loadedBy`/`weighedBy`/`confirmedWith`) — cada vuelo conserva sus propios operadores aunque el ULD sea reasignado/transferido. Las columnas `ulf` (`loaded_by`/`weighed_by`/`confirmed_with`) se mantienen como estado actual/autoritativo, y la nueva tabla `uld_flight_operator` guarda el snapshot por (ULD, vuelo).

| File | Change |
|------|--------|
| `backend/.../uldservice/db/migration/V10__create_uld_flight_operator.sql` ≡ raíz `V61__...` | **NEW** — tabla `uld_flight_operator` (id UUID, `uld_id`, `flight_id`, `loaded_by`, `weighed_by`, `confirmed_with` + timestamps, UNIQUE(uld_id, flight_id), índices por uld y por flight) + **backfill** desde `uld` (`gen_random_uuid()`, PKG 16 lo trae en core) |
| `backend/.../uldservice/entity/UldFlightOperator.java` | **NEW** — entidad JPA por (ULD, vuelo) |
| `backend/.../uldservice/repository/UldFlightOperatorRepository.java` | **NEW** — `findByUldIdAndFlightId`, `findByUldIdInAndFlightId` |
| `backend/.../uldservice/dto/UldDTO.java` | Campo **transitorio** `skipOperatorValidation` (boolean, getter/setter, NO se persiste; Jackson lo acepta en el request) |
| `backend/.../uldservice/service/UldService.java` | Overload nuevo `update(UUID, UldDTO, boolean enforceOperatorMandatory)` |
| `backend/.../uldservice/service/UldServiceImpl.java` | **`validateOperatorsPresent(uld)`**: si `flightId != null` y algún operador en blanco → `IllegalArgumentException` "ULD con vuelo exige Cargado/Pesado/Confirmado" (→ 400 por el `GlobalExceptionHandler`). **`upsertFlightOperator(uld)`**: inserta/reenvía el snapshot (skip si `uld.getId()==null`). **`ensureFlightOperatorCopy(uld, destFlight)`**: al transferir/reasignar copia los operadores actuales del ULD al snapshot del vuelo destino solo si no existe ya (preserva historia). **Lecturas** (`getAll` simple + paginado, `getById`): `applyFlightOperators(list, flightId)`/`applyFlightOperator(dto)` sobrescribe los 3 campos desde el snapshot del vuelo (fallback a columnas). `create`: valida + upsert **salvo `dto.isSkipOperatorValidation()`**; `update`: valida solo si `enforce && !skip`; upsert siempre |
| `backend/.../uldservice/controller/UldController.java` | `PATCH /{id}` → `update(id, dto, false)` (patch parcial no bloqueado); PUT normal usa la ruta que fuerza |
| `backend/.../uldservice/test/.../UldServiceImplTest.java` | Constructor ampliado (mock `UldFlightOperatorRepository`), stubs en tests existentes, **7 tests nuevos**: create con vuelo sin operadores→throws; create con operadores→persiste snapshot; update enforce→rechaza; update patial→no valida; assignFlight copia operadores al destino; getById con snapshot→resuelve operadores del vuelo; getAll→aplica snapshot |
| `frontend/src/stores/ulds.js` | Payload de `dispatchUld` incluye `skipOperatorValidation: !!uld._skipOperatorValidation` |
| `frontend/src/views/UldsView.vue` | Auto-save del escáner (`onUldNumberScanned`) setea `uld._skipOperatorValidation = true` (exento — el operador completará el formulario después) y lo borra tras el dispatch; `saveUld` borra `_skipOperatorValidation` y valida client-side: si hay vuelo y falta algún operador → toast `ulds.operatorsRequired` sin llamar al backend |
| `frontend/src/i18n/es.js` + `en.js` | Clave `ulds.operatorsRequired` |

**Decisiones de diseño**: (1) la **obligatoriedad aplica al guardado por formulario** (POST/PUT) — el auto-save del escáner y el drag a franja flotante quedan **exentos** (flag transitorio, decisión explícita del usuario); (2) `transferUld`/`assignFlight` **copian** los operadores actuales al vuelo destino sin sobrescribir un snapshot ya existente (historia por vuelo); (3) separar columna `uld` (=estado actual) vs `uld_flight_operator` (=cada vuelo), evita mutación destructiva de historia al mover ULDs. Contrato Feign/load-planning intacto (mismos campos en `UldDTO`); `FeignContractSyncTest` 2/2 sin cambios.

**Verificación**: `mvn -o test -pl aircargo-uld-service -am` **BUILD SUCCESS** (common 22 + uld 17 = 39, 15 de `UldServiceImplTest`); frontend `check:refs` 43 SFC 0 refs ✓, `eslint` 0 errores ✓, `vitest` **18/18** ✓, `npm run build` ✓ (2.9s). Sin commits.

## Recent session changes (Sep 5, 2026 (5) — Pasada integral UI/UX: design tokens, cristal, gradientes y animaciones)

**Contexto**: el usuario pidió "usa Tailwind CSS para mejorar la apariencia del frontend con técnicas novedosas — mejóralo todo, desde lo más pequeño a lo más grande; mejora los colores y los temas de UI/UX". Refactor visual global que NO cambia ningún nombre de clase `ds-*` (las vistas no se tocaron); todo el nuevo lenguaje vive en `main.css` (tokens) más 5 componentes compartidos.

| File | Change |
|------|--------|
| `frontend/src/assets/main.css` | **REESCRITO COMPLETO** — nuevo sistema de diseño vía tokens CSS. **Light** (`:root`): `--bg:#eef1f6`, `--text:#0f172a`, `--accent:#2563eb`, `--accent-strong:#1d4ed8`, `--accent-violet:#7c3aed`, `--accent-soft:rgba(37,99,235,.1)`, `--border:#cbd5e1`, `--shadow-xs/sm/lg/glow`, `--gradient-accent:linear-gradient(135deg,#0f172a→#1e3a8a→#2563eb)`, `--gradient-title`. **Tokyo Night** (`[data-theme='tokyo']`): `--accent:#7aa2f7`, `--accent-violet:#a78bfa`, `--border:#334155`, `--gradient-accent`→`#2563eb`, `--gradient-title`→`#7aa2f7/#a78bfa`, `color-scheme:dark`, scrollbar propia. Componentes `ds-*` mejorados: `.ds-page` (aurora + dot-grid overlay), `.ds-card` (hover-lift), `.ds-table-section/header/row` (hover con `inset 3px` del acento en filas), `.ds-btn-primary` (gradiente + glow), `.ds-btn-secondary`, `.ds-btn-danger`, `.ds-modal-backdrop` (glass blur) + `.ds-modal-panel` (pop), `.ds-input` (focus glow), `.ds-title` (texto gradiente), `.ds-tabs`/`.ds-tab` (subrayado gradiente animado via `::after`), `.ds-split-detail` (dot-grid), `.ds-list-card`, `.ds-spinner` (acento). Keyframes nuevos: `ds-spin`, `ds-pop`, `ds-backdrop-in`, `ds-fade-up`, `ds-gradient-pan`, `ds-pulse`. Utilidades: `.glass`, `.text-gradient`, `.glow-accent`, `.glow-soft`, `.shadow-lift`, `.bg-anim`, `.animate-fade-up`, `.animate-pop`, `.animate-pulse-dot`. Scrollbar global (light + tokyo), `::selection` con acento. Nuevas reglas `ds-confirm-backdrop` (cristal) y `ds-confirm-danger` (gradiente rojo) |
| `frontend/src/components/layout/Sidebar.vue` | Logo "AirCargo" con `text-gradient`; icono colapsado con `glow-accent` + `rounded-lg`; `nav-active` con `box-shadow: inset 3px ...` (barra de acento) + bold; `nav-default:hover` con `translateY(-1px)` y acento del icono al 40%; user card: `border:1px solid #e6ecf5` + `box-shadow` + `border-radius:10px` sobre fondo con radial gradient sutil; avatar de rol con `ring-white` + glow (`box-shadow: 0 2px 8px color`) |
| `frontend/src/components/layout/Header.vue` | Gradiente oscuro con `background-size:200% 200%` + **`animation: ds-gradient-pan 18s`** (pan infinito); overlay dot-grid + radiales; línea shine inferior `linear-gradient(90deg, transparent → white .35 → transparent)`; LanguageSwitcher dentro de cápsula glass (`border-white/10` + `bg-white/5` + `backdrop-filter: blur(6px)`); botones de acción (icon lib/font/density/tema) unificados con `border:1px solid rgba(255,255,255,.1)` + `background:rgba(255,255,255,.04)` + `hover:bg-white/10`; separador vertical `border-white/15` |
| `frontend/src/components/ToastNotifications.vue` | **Reescrito** — colores por tipo con gradientes suaves + borde lateral 4px + icono tipo (`✓/✕/⚠/ℹ`): success `#ecfdf5→#d1fae5`/`#10b981`, error `#fef2f2→#fee2e2`/`#ef4444`, warning `#fffbeb→#fef3c7`/`#f59e0b`, info `#eff6ff→#dbeafe`/`#3b82f6`; `border-radius:12px` + `box-shadow` profundo con `inset` superior; clase `.toast-rich` con `.toast-title/.toast-detail/.toast-before/.toast-after`; transiciones slide+fade `cubic-bezier(.22,1,.36,1)`; fuente `var(--font-family)` |
| `frontend/src/components/EmptyState.vue` | Icono dentro de tarjeta con gradiente (`from-slate-100 to-slate-200/60`) + `rounded-2xl` + shadow; contenedor del icono `relative` con `.ds-spinner` overlay al cargar; `animate-fade-up` en el raíz |
| `frontend/src/components/ConfirmDialog.vue` | Migrado al design system: fondo `ds-confirm-backdrop` (cristal blur), panel `ds-modal-panel` + `animate-pop`, título `ds-modal-title`, botones `ds-btn-secondary`/`ds-btn-primary`; confirmación de peligro con `ds-confirm-danger` (gradiente rojo + glow) |

**Decisiones de diseño**: (1) el lenguaje visual nuevo vive 100% en `main.css`/componentes compartidos — **ninguna vista se editó**, por lo que el cambio es reversible si el usuario prefiere otro rumbo; (2) los temas siguen las variables existentes (`data-theme`, `data-font`, `data-density`) y se enriquecen, no se sustituyen; (3) las animaciones respetan `prefers-reduced-motion` donde aplica y son todas `transform/opacity` (GPU). Nota: `tailwind.config.js` NO se extendió (las animaciones van por CSS puro dentro de `@layer utilities/components`).

**Verificación**: `check:refs` 43 SFC 0 refs sin resolver ✓, `eslint` 0 errores ✓, `vitest` **18/18** ✓, `npm run build` ✓ (2.96s). Sin commits.

## Recent session changes (Sep 5, 2026 (6) — Tabla matriz MAWB alineada al design system)

**Contexto**: el usuario pidió mejorar la `<table>` de la vista de MAWBs para que luzca como el design system (cabecera oscura coherente, fondos limpios, tipografía homogénea, sin clases duplicadas). Solo frontend; toda la lógica (filtros, resizers, highlight de vuelo, arcos SVG, info panel, minimapa, export) intacta.

| File | Change |
|------|--------|
| `frontend/src/views/MawbsView.vue` | **Fix contenedor de scroll** — las 3 tablas (matriz, estados, lbs-por-vuelo) envolvían el `<table>` en un `<div>` con la clase `.ds-table-header`, que es una barra de cabecera **grid** (`display:grid; grid-cols-12; padding; gradiente oscuro`) — semánticamente errónea como wrapper de scroll: añadía padding/grid espurios y hacía que las celdas de vuelo vacías (sin `bg-white`) mostraran el gradiente oscuro del contenedor detrás. Ahora el wrapper es neutro (`bg-slate-50/40`); el estilo oscuro vive solo en el `<thead>` (que ya era `sticky`) |
| `frontend/src/views/MawbsView.vue` | **Cabeceras de las 3 tablas** — tono oscuro unificado a `bg-slate-800` (matriz y estados ya tenían el `<tr>`/`th`s en ese tono; se alinearon los `font-black`→`font-bold` y `text-slate-950`→`text-white` en la columna de vuelo resaltada `highlightFlightId`) |
| `frontend/src/views/MawbsView.vue` | **`mawbStatusClass(row)`** — eliminada la duplicación: antes devolvía una cadena base `bg-white px-2 py-2.5 border-r border-slate-300 truncate max-w-[180px] cursor-pointer transition-colors duration-150` CONCATENADA a cada clase de estado, y el `td` ya tenía las mismas clases de layout estáticas → en el DOM aparecían duplicadas `px-2 py-2.5 border-r border-slate-300 ... px-2 py-2.5 border-r border-slate-300`. Ahora devuelve SOLO las clases de estado (texto/bg/border-l-4/hover); el layout vive en el `td` |
| `frontend/src/views/MawbsView.vue` | **Homogeneidad tipográfica** (campaña `text-slate-950`→`text-slate-900` de vistas de datos): celdas de datos de matriz (reserved/received/kg/pcsDispatched + `cellClasses`) y estados (reserved/received/kg/pcsDispatched) pasaron a `text-slate-900`; la línea de consignatario `/` pasó a `text-slate-500` (secundario, igual que en estados); dot de celda vacía `text-slate-200`→`text-slate-300` |
| `frontend/src/views/MawbsView.vue` | **Footers de matriz y estados** alineados al DS: `bg-slate-100 border-slate-300 text-[14px] text-slate-950` → `bg-slate-50 border-slate-200 text-[13px] text-slate-600`; swatch resumen `bg-slate-950`→`bg-slate-800` |
| `frontend/src/views/MawbsView.vue` | **`useHeaderFilters` selector** — los popups de filtro usan `closest('.ds-table-header')` para cerrar al hacer click fuera; al quitar `ds-table-header` de los contenedores, apuntado a `containerSelector: '.ds-table-section'` (mismo patrón de DashboardView) para que los popups sigan cerrándose correctamente |

**Decisión de diseño**: las 3 cabeceras mantienen el lenguaje oscuro (`bg-slate-800`, coherente con la base de `.ds-table-header`); los wrappers de scroll son neutros para que las celdas (todas con `bg-white` o tintos de estado) no filtren fondos oscuros. No se tocó `main.css` ni componentes compartidos.

**Verificación**: `check:refs` 43 SFC 0 refs sin resolver ✓, `eslint` 0 errores ✓, `vitest` **18/18** ✓, `npm run build` ✓ (2.38s). Sin commits.

## Recent session changes (Sep 5, 2026 (4) — Listado vertical de ULDs a la derecha + campos de operador persistidos)

**Contexto**: el usuario aclaró que "la rueda" NO era una franja horizontal de cards debajo del formulario, sino un **listado vertical de ULDs a la derecha del sidebar/formulario**, ordenado del **más pesado al más ligero según gross weight**, y que cada ULD debe mostrar su información operativa persistente (**Weighed by / Loaded by / Confirmed with**).

| File | Change |
|------|--------|
| `frontend/src/views/UldsView.vue` | **Layout: franja inferior → panel lateral derecho** — el wrapper pasó de `flex flex-col` (con franja inferior) a **`flex flex-col lg:flex-row`**: `.ds-split-detail` (formulario) queda a la izquierda (`flex-1` sin cambios; `min-w-0` para no desbordar) y el **aside ahora es `shrink-0 w-full lg:w-[420px] border-t-2 lg:border-t-0 lg:border-l-2`** (borde superior en móvil / **borde lateral en ≥lg**), `flex flex-col min-h-0` con lista **vertical** `flex flex-col gap-1 content-start` (ya NO `grid-cols-*`). En móvil queda apilado (formulario primero, listado abajo); en desktop el listado queda **a la derecha del formulario**. Header del aside conserva chips `{n} ULDs` + badge emerald ✓ llenos + badge ámbar ◍ casi llenos, y añade **`↓ {t('ulds.byGross')}`** (ml-auto) con tooltip "Ordenados por peso bruto desc" |
| `frontend/src/views/UldsView.vue` | **Orden por peso** — nuevo computed **`sortedUlDsByGross`** (~L557): copia `filteredUlDs` y ordena **descendente por `grossWeightLbs`** (`(b.grossWeightLbs||0) - (a.grossWeightLbs||0)`). El `v-for` del aside ahora itera `sortedUlDsByGross` (los chips `fullUldCount`/`nearFullUldCount` siguen sobre `filteredUlDs` = el mismo universo) |
| `frontend/src/views/UldsView.vue` | **Fila del listado enriquecida** — tercera línea nueva por card: **`Weighed by: X | Loaded by: Y | Confirmed with: Z`** en `text-[10px] font-mono`, reutilizando las claves `ulds.form.{weighedBy,loadedBy,confirmedWith}` (labels con `<b>` del valor persistido; `—` si vacío). El peso bruto ya se mostraba en la segunda línea (`{grossWeightLbs} lb`) y se resalta en `text-slate-700 tabular-nums` |
| `frontend/src/i18n/es.js` + `en.js` | Claves `ulds.byGross` ('por peso' / 'by weight') |
| Backend verificado (sin cambios) | **Persistencia completa de los 3 campos ya existente y confirmada**: entidad `Uld.java` (`loadedBy`→`loaded_by`, `weighedBy`→`weighed_by`, `confirmedWith`→`confirmed_with`), `UldDTO.fromEntity/toEntity` mapean los 3, migraciones `V4__add_confirmed_with_completed_at_to_uld.sql` (confirmed_with) y `V8__rename_built_by_to_loaded_by_add_weighed_by.sql` (rename + weighed_by), `UldServiceImpl.update` los copia null-safe, y el frontend ya los envía en `stores/ulds.js` (~115-117), `stores/app.js` (~200-202) y la rama flotante de `saveUld` (~1068-1070) |

**Verificación**: `check:refs` 43 SFC 0 refs sin resolver ✓, `eslint` 0 errores ✓, `vitest` **18/18** ✓, `npm run build` ✓ (2.51s). **E2E real vía gateway** (token HS512 minted con `JWT_SECRET` del `.env`): `PUT /api/ulds/{bcda2590}` con `{loadedBy:'E2E LOADER', weighedBy:'E2E WEIGHER', confirmedWith:'E2E CONFIRMED'}` → **200**, relectura `GET /api/ulds/{id}` devuelve los 3 intactos, y verificación directa en BD (`psql`) confirma los 3 valores persistidos. ULD de prueba restaurado a campos NULL tras el E2E. Sin commits.

## Recent session changes (Sep 5, 2026 (3) — La rueda de ULDs en franja inferior + cierre de homogeneidad Dashboard)

**Contexto**: el usuario pidió "mejorar la rueda" — que las card views de ULDs de `/ulds` se muestren **hasta abajo**, visualizando todos los ULDs llenos (antes: grid lateral `ds-split` de cards + formulario a la derecha; la card que se clickeaba desplegaba el formulario). Aclaración: "la rueda" NO es un círculo/ruleta, son las cards de ULDs. Además se cerró la homogeneidad tipográfica de la tabla del Dashboard (último header claro + celdas con `text-slate-950` que quedaban).

| File | Change |
|------|--------|
| `frontend/src/views/UldsView.vue` | **Rediseño "la rueda" → franja inferior**: el wrapper ya no usa `.ds-split` (grid lateral) sino `flex-col overflow-hidden`: formulario de detalle arriba dentro de `.ds-split-detail` (`flex-1 min-h-0 overflow-y-auto bg-slate-50`) y **aside inferior** (`shrink-0 border-t-2 border-slate-200 bg-slate-50/60`) con header de resumen + grid de cards `grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-1.5 content-start` (scroll y/x cuando hay muchos ULDs). Cards: **ULLD lleno** (`volumePct>=100`) resaltado `border-emerald-300 ring-1 ring-emerald-100` con barra `bg-emerald-500`; casi lleno (>=90) barra `bg-slate-600`; resto barra `bg-slate-300`; barra cortada a `Math.min(volumePct,100)`. El input `%` del formulario se enlaza al card en vivo. Chips del header: `{n} ULDs` + badge emerald ✓ `{n} llenos` + badge ámbar ◍ `{n} casi llenos` |
| `frontend/src/views/UldsView.vue` | **Computeds nuevos** `fullUldCount` / `nearFullUldCount` (tras `filteredUlDs` ~L555): cuentan ULDs con `volumePct>=100` y `>=90 && <100` según la lista filtrada (mismo universo que los cards). El click en card sigue desplegando el formulario (se mantiene el `expandedUld` actual) |
| `frontend/src/i18n/es.js` + `en.js` | Claves `ulds.summary` ('ULDs'), `ulds.fullCount` (`'{n} lleno | {n} llenos'` / `'{n} full | {n} full'`), `ulds.nearFullCount` (`'{n} casi lleno | {n} casi llenos'` / `'{n} near full | {n} near full'`) |
| `frontend/src/assets/main.css` | **Reglas huérfanas eliminadas**: `.ds-split` y `.ds-split-list` (grid lateral que ya no se usa en ningún SFC) + su `@media (min-width: 1024px)` y los overrides compact/tokyo de `ds-split-list`. Se conservan `.ds-split-detail` (`flex-1 min-h-0 overflow-y-auto bg-slate-50`) y `.ds-list-card`. **Tokio night**: `bg-slate-50/60` del aside ya tiene override (~L636) → el fondo oscurece bien en dark; los badges emerald/amber no tienen override tokyo (aceptado como acentos de estado) |
| `frontend/src/views/DashboardView.vue` | **Cierre homogeneidad**: las 2 celdas de datos que quedaban con `text-slate-950` (flight number y grossLbs del body, ~L201/L212) → `text-slate-900` (mismo criterio de la campaña "0 slate-950 en datos"). El header claro `bg-slate-100` del Dashboard se conserva (estética deliberada sticky con filtros; las grillas del DS usan `ds-table-header` oscuro pero el Dashboard mantiene su lenguaje visual propio) |

**Verificación**: `check:refs` 43 SFC 0 refs sin resolver ✓, `eslint` 0 errores ✓, `vitest` 18/18 ✓, `npm run build` ✓ (2.65s). Sin commits.

## Recent session changes (Sep 5, 2026 (2) — Propagación automática a load-planning FIX + Material Design Icons + homogeneidad Bookings)

**Contexto del fix crítico**: el usuario reportó que load-planning "sigue sin actualizarse" al cambiar commodity/status de un MAWB desde Bookings. La cadena AMQP existía (mawb-service publica `mawb.updated`/`mawb.status.changed`, bindings en `aircargo.loadplanning.invalidate`), pero **el listener tenía DOS `@RabbitListener` sobre la misma cola** → RabbitMQ hacía round-robin entre 2 consumidores → ~50% de eventos perdidos (un `MawbUpdatedEvent` caía en el handler de `UldUpdatedEvent`). Patrón correcto: **un único `@RabbitListener` + un `@RabbitHandler` por tipo** (el mismo que ya usaba `NotificationEventListener`). **E2E verificado**: PUT commodity LIVE_PLANTS en `406-05912981` → log `load-plans invalidado por cambio en 406-05912981` → plan fresco con `desc=LIVE_PLANTS` (antes tardaba/fallaba) → también el sync de `uld_awb` (LIVE_PLANTS|BOOKED); restaurado a DRY_CARGO vía API (sync automático). Verificar siempre el nº de consumidores de la cola tras tocar listeners (`rabbitmqctl list_queues`): antes 2, ahora 1.

| File | Change |
|------|--------|
| `backend/.../loadplanningservice/listener/PlanInvalidatorListener.java` | **FIX** — de 2 `@RabbitListener` a **un solo `@RabbitListener(queues = "aircargo.loadplanning.invalidate")`** con `@RabbitHandler(MawbUpdatedEvent)` → `invalidate(awbNumber)` y `@RabbitHandler(UldUpdatedEvent)` → `invalidate(uldNumber)` (ambos llaman `invalidate` que hace `cache.clear()` sobre `"load-plans"`, log de invalidación, nunca lanza) |
| `frontend/src/utils/mdiIcon.js` | **NEW** — adaptador `mdiIcon(path)` que convierte un path de `@mdi/js` (string SVG) en un componente funcional Vue compatible con el patrón `<component :is size stroke-width>` (renderiza `<svg viewBox="0 0 24 24" fill="currentColor">`) |
| `frontend/src/mdi.js` | **NEW** — mapa `mdiIcons` con los 44 iconos del design system mapeados a Material Design Icons (`mdiRadar`, `mdiAlertCircle`, `mdiAlert`, `mdiApi`, `mdiSwapHorizontal`, `mdiCalendarMonth`, `mdiCamera`, `mdiCheck`, `mdiChevronRight`, `mdiClipboardList`, `mdiCrown`, `mdiDownload`, `mdiEye`, `mdiFileDocument`, `mdiFileExport`, `mdiReceiptText`, `mdiFileUpload`, `mdiForklift`, `mdiGauge`, `mdiKey`, `mdiViewGrid`, `mdiPageLayoutSidebarLeft`, `mdiLock`, `mdiLogout`, `mdiMenu`, `mdiWeatherNight`, `mdiPackageVariant`, `mdiPaperclip`, `mdiPencil`, `mdiAirplaneTakeoff`, `mdiPlus`, `mdiRefresh`, `mdiRoutes`, `mdiScale`, `mdiMagnify`, `mdiCog`, `mdiShieldLock`, `mdiShieldAlert`, `mdiWeatherSunny`, `mdiDelete`, `mdiAccount`, `mdiAccountGroup`, `mdiClose`, `mdiStore`) |
| `frontend/src/composables/useIcons.js` | `useIcons()` devuelve `mdiIcons` cuando `iconLib.value === 'mdi'` (antes solo tabler/lucide) |
| `frontend/src/utils/iconLib.js` | `VALID = ['tabler', 'lucide', 'mdi']`; `toggleIconLib()` cicla los 3 |
| `frontend/src/components/layout/Header.vue` | Botón de iconos cicla TB→LC→MD con tooltips por modo |
| `frontend/package.json` | Dependencia `@mdi/js` (^7.4.47) |
| `frontend/src/views/BookingsView.vue` | **Homogeneidad tipográfica** (menos fatiga visual): popups de filtro `text-[13px] text-slate-950` → `text-[12px] text-slate-700` (7 columnas, escala secundaria uniforme); flechas de filtro `text-[10px]` → `text-[11px]`; badge de unidades `text-[11px]` → `text-[12px]`; input AWB del modal `text-[14px]` → `text-[13px]`; queda 1 solo `text-slate-950` (hover del botón cerrar, aceptable) |
| `frontend/src/views/LoadPlanningView.vue` | **Auto-refresh near-live**: `autoRefreshId = setInterval(refreshIfIdle, 20000)` en `onMounted`; `refreshIfIdle()` re-fetcha `loadUldsForFlight` + `fetchLoadPlan` SOLO si hay vuelo, `document.visibilityState === 'visible'`, sin drag en curso (`isDragging`, set en `onTableUldPointerDown/Up`) y sin modales abiertos (`pendingTransfer`, `pendingFlightPick`, `showFlightPicker`, `showUndoToast`); limpieza en `onUnmounted` |

**Verificación**: frontend check:refs ✓ 43 SFC 0 refs, lint ✓ 0 errores, vitest **18/18**, `npm run build` ✓ (2.5s). Backend: jar load-planning reconstruido (`rm target/*.jar` + `mvn -o -q -pl aircargo-load-planning-service install -DskipTests` EXIT=0, 107.9MB), servicio relanzado en 9098 (PID 266524), cola `aircargo.loadplanning.invalidate` con **1 consumidor**. E2E de propagación completo documentado arriba. Sin commits.

## Recent session changes (Sep 5, 2026 — Bookings y demás grillas: reducción de fatiga visual — tipografía homogénea + iconos)

Revisión de las vistas de datos (Bookings, Flights, Load Planning, Receipts) para reducir la fatiga visual mediante tipografía homogénea (`font-black`→`font-bold`, `text-slate-950`→`text-slate-900`) y sustitución de glifos unicode por iconos del design system.

| File | Change |
|------|--------|
| `frontend/src/views/BookingsView.vue` | **Tipografía homogénea**: `font-black` → `font-bold`, `text-slate-950` → `text-slate-900` en todas las filas de datos (AWB, cliente, shipper, pieces, weight, unit badges, estado MAWB, K suffix); cabecera de tabla import `font-black` → `font-bold` 12px slate-700; stat rows del import `text-slate-950` → `text-slate-600` 12px (subsidiario, no competente con los datos) |
| `frontend/src/views/BookingsView.vue` | **Iconos del design system**: botones Importar/Exportar/Nuevo reemplazan glifos unicode `↑`/`↓`/`+` por `icons.FileUpload`/`icons.Download`/`icons.Plus` (Tabler/Lucide, 14px, stroke-width 2); indicador RECEIVED reemplaza `&#10003;` por `icons.Check` emerald-600 13px; botón cerrar del modal import normalizado a slate-400 con transición (antes slate-950 sin hover) |
| `frontend/src/views/BookingsView.vue` | **Eliminación de ruido visual**: columna MAWB status en la cabecera pierde el bloque decorativo `bg-slate-800 py-0.5 rounded border border-slate-600` (restaurado a cabecera uniforme sin background propio, consistente con el gridview uniforme); separador `·` trailing en la celda de estado MAWB eliminado (ruido sin función); select de vuelo `tracking-widest` → `tracking-wider`, `font-black` → `font-bold` |
| `frontend/src/views/BookingsView.vue` | **Entrada del selector de vuelo**: label `text-[13px] font-black text-slate-950` → `text-[12px] font-bold text-slate-600` (suavizado para no competir visualmente con la tabla); select `font-black` → `font-bold` |
| `frontend/src/views/BookingsView.vue` | Import modal: subtítulo `text-[13px] text-slate-950` → `text-[12px] text-slate-600` (delegado como contexto secundario); filas de preview `text-slate-950` → `text-slate-900` (peso consistente) |
| `frontend/src/views/FlightsView.vue` | Grilla de datos: 6 celdas (airline+flight, origin→dest, aircraftType, aircraftReg, flightDate, totalPositions, maxPayloadKg) `font-black text-slate-950` → `font-bold text-slate-900` (la etiqueta de aerolínea mantiene su pill `bg-slate-800` — es jerarquía por diseño) |
| `frontend/src/views/LoadPlanningView.vue` | Header meta-vuelo: 3 valores (airline, aircraftReg, ruta) `font-black text-slate-950` → `font-bold text-slate-900`; stats de cabecera y positionSummary `text-slate-950` → `text-slate-900`; tarjetas ULD (`uld`, status, N MAWB, weight, select transferencia) y detalle grain (ULD, texto vacío-ULD) normalizados a font-bold/slate-900; select de transferencia y texto del modal de transferencia ídem |
| `frontend/src/views/WarehouseReceiptsView.vue` | Badges "N HAWBs"/"1 HAWB" `font-black` → `font-bold` (slate-600/slate-900); select de vuelo `font-black tracking-widest` → `font-bold tracking-wider` (mismo patrón que Bookings) |
| `frontend/src/views/SecurityView.vue` | 3 títulos de sección (`h2` activeSessions/auditLog/userBlock) `font-black` → `font-bold` |
| `frontend/src/views/RampUploadView.vue` | Mensajes de estado (error/success) `font-black text-slate-950` → `font-bold text-slate-900` (el resto de la vista conserva su estética terminal fullscreen: título, badge "Excel Parser Active", botón) |
| `frontend/src/views/{MawbsView,UldsView,RampUploadView,WarehouseReceiptsView}.vue` + 9 componentes + `main.css` | **Homogeneización TOTAL** (decisión del usuario): TODAS las `font-black` restantes → `font-bold`, incluidas las que eran estética deliberada — MawbsView (cabeceras chalkboard `bg-slate-700`, stats, infoPanel, th del detalle), UldsView (número de tarjeta, badges, palletSheetHeader, netWeight, steppers, botón), RampUploadView (título, badge, drag-text, botón), stepper del wizard de recepciones, y el design system (`ds-modal-title`, `ds-title`, `ds-btn-*`). Resultado: **0 `font-black` en todo `src/`** |

**Verificación**: `check:refs` ✓ (43 SFC), `lint` ✓ (0 errores), `vitest` **18/18**, `build` ✓ (exit 0, 2.5s). Sin commits.

**Resumen de sesiones del día**: la sesión cubrió (1) fix de RabbitMQ (infra, AMQP propagation confirmada E2E), (2) reducción de fatiga visual en Bookings (tipografía homogénea, iconos del design system, eliminación de ruido visual), (3) **extensión a las demás grillas**: `FlightsView` (`font-black`→`font-bold`, `text-slate-950`→`text-slate-900` en las 6 celdas de datos), `LoadPlanningView` (header meta-vuelo + stats + tarjetas ULD + detalle grain normalizados a font-bold/slate-900, select de transferencia ídem), `WarehouseReceiptsView` (badges "N HAWBs" y select de vuelo) y (4) **cierre de vistas restantes**: `SecurityView` (3 títulos de sección) y `RampUploadView` (mensajes de estado error/success). (5) **Homogeneización TOTAL** (decisión del usuario): los `font-black` que quedaban como estética deliberada — MawbsView (chalkboard `bg-slate-700`), UldsView (tarjetas), RampUploadView (terminal fullscreen) y el stepper del wizard de recepciones — más 9 componentes y el design system (`ds-modal-title`, `ds-title`, `ds-btn-*`) también pasaron a `font-bold`. **Resultado global: 0 `font-black` en todo `src/`**. (6) **Publisher `flight.departed` (cierre del gap de la bitácora)**: `LoadPlanningServiceImpl.closeLoadPlan` ahora publica `FlightDepartedEvent` en el exchange `aircargo.events` con routing key `flight.departed` (inyectado `RabbitTemplate`, helper `publishFlightDeparted` best-effort sin romper el cierre). La cola `aircargo.notifications` ya tenía el binding — con esto el ciclo event-driven completo: cierre de vuelo → notificación "Vuelo Ha Partido" para todos los usuarios. Servicio reiniciado en 9098 (PID 200124, jar 1.2.0-SNAPSHOT), health 200, binding verificado en el broker (`aircargo.events → aircargo.notifications / flight.departed`). Complilado con `-o -pl load-planning -am install` EXIT=0. Sin commits.

(7) **E2E del ciclo de cierre + 2 fixes reales encontrados en el camino**:
| File | Change |
|------|--------|
| `backend/.../flightservice/controller/FlightController.java` | **FIX contrato Feign** — `PUT /api/flights/{id}/status` esperaba `@RequestBody FlightStatus` (JSON enum) pero `FlightClient.updateFlightStatus(UUID, String)` envía `text/plain` → 500. Ahora acepta `@RequestBody String rawStatus` y parsea `FlightStatus.valueOf(rawStatus.trim().toUpperCase())` (valor inválido → `HttpMessageNotReadableException` → 400). Rebuild + restart flight-service (PID 206837, 9093) |
| `backend/aircargo-common/.../auth/JwtAuthFilter.java` | **FIX tokens de servicio muertos por la revocación por-request** — los tokens Feign de servicio (subject `service:<svc>`) pasaban por `jdbcTemplate` y `UUID.fromString(userId)` lanzaba `IllegalArgumentException` → catch → **401 "Token expired or invalid"** en TODOS los servicios con BD. Ahora el chequeo de revocación se salta si `userId.startsWith("service:")` (además de try/catch defensivo). Este fix destrabó el listener: notification → `GET /api/users` de auth pasaba de 401 a 200 |
| `AGENTS.md` | **Lección Maven** — `mvn -o -q -pl <svc> -am install` NO regenera el boot fat jar si en el módulo no cambió código (tras editar common, el jar de auth seguía con el common viejo embebido, por eso el fix aislado no surtía efecto). **Siempre borrar el `target/*.jar` del servicio antes de reinstalar** tras tocar common |

**E2E real cerrado**: vuelo de prueba `f0c18fa5` (0335, UPS, SDQ→MIA, sin ULDs) → `POST /api/load-planning/flight/{id}/close` con token HS512 minted (jjwt, header `typ:JWT` obligatorio — el mint Python con header sin `typ` daba 401) → 200 → log LP `Published flight.departed event for flight f0c18fa5…` → notification listener `Received flight.departed event: FlightDepartedEvent[flightId=f0c18fa5…, flightNumber=0335, airlineId=0000…0001]` → **20 notificaciones** `EMAIL` "Vuelo Ha Partido" / "El vuelo 0335 ha partido." (1 por usuario, `entity_type=FLIGHT`, `entity_id=f0c18fa5…`, `is_read=f`) en `notification.notification` → 0 "Failed to process" en el log. Vuelo quedó en `DEPARTED` (se restauró a SCHEDULED antes del cierre definitivo vía SQL para probar limpio). Nota: el primer intento con el auth jar viejo falló (401) — tras el fix de common + jar regenerado (`rm target/*.jar`) el flujo quedó verde. **Propagación del fix**: el common corregido se reinstaló en TODOS los jars (`mvn -o install -DskipTests` tras borrar los 8 fat jars), el stack completo se reinició (setsid nohup & disown; el subshell `( & )` muere con el runner) y los 10 servicios quedaron UP con 0 errores de arranque. Sin commits.

## Recent session changes (Sep 5, 2026 — Propagación event-driven de cambios de MAWB por AMQP + RabbitMQ restaurado)

**Contexto y problemas resueltos**: cambios de commodity/status de una MAWB debían propagarse automáticamente a los `uld_awb` (uld-service) y a `load-planning/` (cache eviction + lectura fresca) sin re-guardar el ULD — TODO vía AMQP con `Jackson2JsonMessageConverter` (los records de `common/event` no son Serializable).

**Diagnóstico RabbitMQ (bloqueante)** — el stack estaba SIN broker alcanzable: el contenedor Docker `aircargo-rabbitmq` (rabbitmq:4.0-management-alpine, `RABBITMQ_DEFAULT_USER=aircargo`) llevaba 13h **unhealthy con puerto NO publicado** (`PortBindings` malformados `{invalid IP 5672}` — mapping de compose mal parseado al crearse), el broker del SISTEMA (Fedora, RabbitMQ 4.2.9) ocupaba `:5672` con solo el usuario `guest` (verificado por handshake AMQP crudo: `guest/guest` → `connection.tune`, `aircargo/<pw>` → `Connection reset` = usuario inexistente), y `.env` apuntaba a `RABBITMQ_PORT=5673` (sin listener). Es decir, el Rabbit del stack SIEMPRE fue el contenedor; en ese estado era inalcanzable. **Fix infra**: `docker/docker-compose.infrastructure.yml` rabbitmq `ports` → `"${RABBITMQ_PORT:-5672}:5672"`; `docker compose up -d rabbitmq` recreó la instancia → healthy publicando `0.0.0.0:5673->5672/tcp`; auth `aircargo` en 5673 OK. El `/etc/rabbitmq/rabbitmq.conf` del sistema es el ejemplo stock (todo comentado).

| File | Change |
|------|--------|
| `backend/aircargo-load-planning-service/pom.xml` | Añadido `spring-boot-starter-amqp` |
| `backend/.../loadplanningservice/config/RabbitConfig.java` | **NEW** — TopicExchange `aircargo.events`; cola durable `aircargo.loadplanning.invalidate`; bindings `mawb.updated` + `uld.updated`; `jackson2JsonMessageConverter` |
| `backend/.../loadplanningservice/listener/PlanInvalidatorListener.java` | **NEW** — consume `MawbUpdatedEvent`/`UldUpdatedEvent` y `cacheManager.getCache("load-plans").clear()` (mejor-effort, nunca lanza) |
| `backend/.../loadplanningservice/service/LoadPlanningServiceImpl.java` | Enriquecimiento en lectura: `enrichFromMawb` sobreescribe `description`/`status`/`destination` del link con MAWB viva (`MawbClient.getMawbById`, fallback `getMawbByAwbNumber` con `normalizeAwb` a `XXX-XXXXXXX`, try/catch por link) → `GET /api/load-planning/flight/{id}` siempre refleja commodity/status frescos aunque el link esté desactualizado |
| `backend/.../loadplanningservice/src/main/resources/application.properties` | Bloque `spring.rabbitmq.host/port/username/password` env-driven + `spring.rabbitmq.listener.simple.missing-queues-fatal=false` |
| `backend/.../notificationservice/config/RabbitConfig.java` | Bean `jackson2JsonMessageConverter` + `retryListenerFactory(ConnectionFactory, Jackson2JsonMessageConverter)` seteado con `factory.setMessageConverter(...)` |
| `backend/.../uldservice/src/test/.../UldServiceImplTest.java` | `@Mock RabbitTemplate` + import; test `delete_returnsFalse_whenNotExists` pasó a `findById` (el service cambió de `existsById`) |
| `backend/.../mawbservice/src/test/.../MawbServiceImplTest.java` | `@Mock RabbitTemplate` (constructor ahora 3 args con RabbitConfig.EXCHANGE constante) |
| `frontend/src/views/LoadPlanningView.vue` | Mapeo de items con `status: m.status \|\| ''` (~548); **badge de status dentro de la celda MAWB** (~219-223) con helper `mawbStatusBadge` (~508) con los mismos colores que BookingsView (RECEIVED ámbar, MANIFESTED verde, DEPARTED/ARRIVED azul, BOOKED gris claro); export XLSX añade `' ['+status+']'` al mawb (~1202) |
| `docker/docker-compose.infrastructure.yml` | rabbitmq `ports` → `"${RABBITMQ_PORT:-5672}:5672"` (alineado con `.env=5673`; evita conflicto con el broker del sistema en 5672) |

**E2E real vía gateway** (token HS512 minted con el `JWT_SECRET` del `.env`): plan `0403` (vuelo `b1d09efe…`) lleno en caché (link de `406-05912981` mostraba GENERAL/'' en BD pero el plan ya devolvía DRY_CARGO/BOOKED por el enriquecimiento); `PUT /api/mawbs/{cc518ce9…}` con `commodityType=LIVE_PLANTS` → 200 → **listener uld**: log `mawb.updated 406-05912981 -> 1 uld_awb link(s) sincronizados (commodity=LIVE_PLANTS, status=BOOKED)` y BD `uld_awb` → `LIVE_PLANTS|BOOKED|MIA`; **listener load-planning**: log `load-plans invalidado por cambio en 406-05912981`; segundo `GET /api/load-planning/flight/…` fresco (`desc=LIVE_PLANTS`) demostrando evicción de caché. Restaurado a `DRY_CARGO` (BD final `DRY_CARGO|BOOKED`). Los 10 servicios UP (notification health muestra `rabbit 4.0.9` UP). Nota: el `ERROR audit_log_user_id_fkey` en el log de mawb es artefacto del token minted con userId falso (AuditService traga el fallo y no rompe la operación) — no es bug del flujo.

**Verificación**: backend compile offline limpio; `mvn -o test` common 22 + uld 11 + mawb 11 BUILD SUCCESS; `mvn -o install -DskipTests … -am` EXIT=0 (jars 1.2.0 SNAPSHOT reconstruidos). Frontend: `check:refs` 43 SFC 0 refs OK, eslint 0 errores, vitest **18/18**, `npm run build` OK. Servicios relanzados: pids 138418 (mawb), 138422 (uld), 138426 (load-planning), 138430 (notification), logs en `~/aircargo-logs/`. Sin commit.

Dos problemas reportados sobre el formulario de ULD: **el campo "Confirmed with" nunca se guardaba** y el **commodity de la MAWB solo se reflejaba en un ULD**. Además se cerró el diagnóstico del 404 al navegar. Sin commit.

**Diagnóstico del "Whitelabel 404" (visto al recargar/navegar, 22:25)**: NO era bug de código — el **gateway murió a las 22:21** (log de `~/aircargo-logs/aircargo-gateway.log` terminó abrupto, PID 63451, sin shutdown hook) y **todos los servicios 9092-9100 se apagaron limpiamente a las 22:25:15**. Con el stack caído, Vite/nginx devolvía el Whitelabel/genérico al navegar. Al reiniciar el stack (usuario) todo quedó sano y el 404 no reapareció. Lección: ante Whitelabel/conexión, primero comprobar los listeners del stack (`ss -tlnp`) antes de buscar bugs.

| File | Change |
|------|--------|
| `frontend/src/stores/ulds.js` | **FIX (confirmedWith nunca se guardaba)** — `dispatchUld` (ruta principal de guardado) omitía `confirmedWith` en el payload; añadido `confirmedWith: uld.confirmedWith ?? null` junto a `loadedBy`/`weighedBy` |
| `frontend/src/stores/app.js` | Ídem — `dispatchUld` (`~187-202`) omitía `confirmedWith` en el payload PUT; añadido |
| `frontend/src/views/UldsView.vue` | Ídem — la rama flotante de `saveUld` (ULD sin vuelo, ~1051-1053) también omitía `confirmedWith`; añadido. El display ya mapeaba los 3 campos (`rebuildLocalList` 878-882: `u.loadedBy/weighedBy/confirmedWith \|\| ''`) |
| Backfill BD (SQL directo, no migración) | **Re-sync total del commodity** — `UPDATE uld_awb SET description = m.commodity_type::text FROM mawb m WHERE (ua.mawb_id=m.id OR regexp_replace(label,'[\s\-_/]','','g')=regexp_replace(m.awb_number,'[\s\-_/]','','g')) AND ua.description IS DISTINCT FROM m.commodity_type::text` (el join por label cubre links con mawb_id NULL o formatos mixtos; la MAWB es la única fuente de verdad). **2 links corregidos** (GENERAL → DRY_CARGO): AAD99387UPS (59 966) y AAY47354UPS (58 964); los otros 4 ya calzaban. Verificado: 0 links divergentes tras el UPDATE. Nota: el mismo resultado se logra re-guardando el ULD desde la UI (el front ya resuelve el commodity en vivo), el backfill solo evita la espera |
| Backend verificado (sin cambios) | `UldServiceImpl.update` (183-185) copia `loadedBy`/`weighedBy`/`confirmedWith` null-safe y `UldDTO.fromEntity/toEntity` los mapean — **E2E real vía gateway**: PUT con los 3 campos → 200 y re-GET los devuelve intactos (proceso en marcha usa el jar nuevo; los datos de prueba se revirtieron a null) |

**Verificación**: frontend `check:refs` 43 SFC 0 refs, eslint 0 errores, vitest **18/18**, `npm run build` OK. E2E: GET `/api/ulds` devuelve `loadedBy`/`weighedBy`/`confirmedWith` (claves presentes en los 4 ULDs); `GET /api/cargo/mawbs` devuelve commodity fresco (59 966→DRY_CARGO, 58 964→DRY_CARGO, 59 981→GENERAL, 58 849→DRY_CARGO). El `uid` de prueba AAY55741UP quedó restaurado a sus valores originales.

## Recent session changes (Sep 4, 2026 (4) — Perf del form ULD + commodity sincronizado con Bookings + skids=piezas en import XLSX)

Tres arreglos pedidos sobre el formulario de ULDs y el módulo de Bookings. Sin commit.

| File | Change |
|------|--------|
| `frontend/src/views/UldsView.vue` | **Fix 1 (slowmotion)**: el panel de detalle completo (form MAWB, inputs, scan) se renderizaba en el DOM para TODOS los ULDs filtrados (`v-for="uld in filteredUlDs"` + `v-show="expandedUldId === uld.uid"`), re-renderizándose en cada interacción → se sentía como slowmotion. Ahora se renderiza **UN solo panel**: `expandedUld = computed(() => localUlds.find(u => u.uid === expandedUldId))` y el template usa `<template v-if="expandedUld"><div v-for="uld in [expandedUld]" ...>` (mantiene el nombre `uld` intacto en el cuerpo), cerrándose con `</template>`. |
| `frontend/src/views/UldsView.vue` | **Fix 2 (commodity antiguo)**: `rebuildLocalList` copiaba el descriptor del link `uld_awb` (`m.description`, congelado al momento del save del ULD) → la descripción del commodity en la MAWB del ULD no reflejaba los cambios hechos en Bookings. Nuevo helper `liveCommodityFor(awbNumber, fallback)` que resuelve el commodity desde `appStore.mawbs` (frescos) por `awbNumber`, con fallback al descriptor almacenado; usado en `commodityType`/`commodityHint` de cada MAWB del ULD |
| `frontend/src/views/BookingsView.vue` | **Fix 2 parte 2**: al **editar** un booking (modal), ahora también actualiza la MAWB vinculada vía `mawbsApi.update(m.id, {commodityType, destination})` (endpoint ya era null-safe en `MawbServiceImpl.update`) → el cambio de commodity de Bookings persiste en el MAWB y el form ULD lo refleja al recargar (try/catch, nunca rompe el guardado del booking). Import de `mawbsApi` añadido |
| `frontend/src/views/UldsView.vue` | **Fix 2 parte 2b (revisión "aún no actualiza")**: `liveCommodityFor` ahora también cae al commodity del **Booking** (`appStore.bookings` por `awbNumber`) cuando la MAWB no tiene kind fresh/null-comunidad en `appStore.mawbs`; `rebuildLocalList` usa este helper en `commodityType`/`commodityHint`; `onMounted` ahora carga también `appStore.loadBookings()` (fuente en vivo sin la MAWB como único origen) |
| `frontend/src/views/BookingsView.vue` | **Fix 2 parte 2c**: `bookingMawb(b)` dejó de exigir `b.mawbId` (retorna `null` antes de buscar) — ahora matchea por `m.id === b.mawbId` **o** `m.awbNumber === b.awbNumber`, cubriendo bookings sin `mawbId` (p.ej. históricos/importados) para que el PUT de commodity a la MAWB sí se dispare al editar |
| `frontend/src/views/UldsView.vue` | **Fix 2 parte 2d ("solo un ULD se actualizó")**: los enlaces `uld_awb.mawb_label` históricos tienen formatos mixtos (`40605857585` vs `406-05857585`/con espacios); el lookup de `liveCommodityFor` era por igualdad EXACTA → solo matcheaba el ULD cuyo label coincidía con el formato de `appStore.mawbs` (por eso 1 ULD sí, el resto no). Nuevo helper `normalizeAwb(raw)` (limpia `[\s\-_/]`, 11 dígitos → `XXX-XXXXXXXX`, copiado de BookingsView); `rebuildLocalList` normaliza el `awbNumber` de cada fila (y al re-guardar el link vía `saveUld` el `mawbLabel` queda canónico → higiene de datos progresiva); `liveCommodityFor`/`mawbReceiptInfo`/`mawbInBookings`/`onMawbSelect`/`mawbSelectGroups` comparan con `normalizeAwb` en ambos lados. Así TODOS los ULDs que contienen la MAWB resuelven el mismo commodity fresco |
| `frontend/src/views/BookingsView.vue` | **Fix 3 (import XLSX)**: (a) `parseBookingsFromXLSX` truncaba números con separadores de miles: `parseInt('1,250') → 1` — por eso "ciertas MAWBs" mostraban cantidad 1 en vez de las piezas reales. Nuevos `toInt(v)`/`toFloat(v)` que limpian `[,\s]` antes de parsear, usados en skids/units/reservedKg/priority. (b) cuando una MAWB solo trae piezas/cajas (sin skids), la cantidad debía ser igual a las piezas → `skids: row.skids || row.units || 1` (era `row.skids || 1`, inventaba 1) en `confirmImport`; mismo fallback en `saveBooking` (`form.value.skids || form.value.units || 1`) |

**Verificación**: check:refs ✓ 43 SFC 0 sin resolver, eslint ✓, vitest **18/18** ✓, `npm run build` ✓.

## Recent session changes (Sep 4, 2026 — Entrega a Version1.4 + Tipografía global Bodoni/Cascadia/combo + limpieza de Dashboard Builder en Version1.3)

**Contexto**: el commit `2fd5341` con el Dashboard Builder (tabla/pivot/gráficos) se subió por error a `aircargo-saas-Version1.3`. Se decidió: (1) **quitar el Builder de Version1.3** (conservando EA type, header filters y el fix visual de DashboardView) y (2) **entregar el proyecto completo CON builder en `aircargo-saas-Version1.4`** (repo nuevo, estaba vacío). Además se aplicó un cambio de tipografía global (Bodoni / Cascadia Code SemiBold / combo).

**Operación de repos** (verificada por API de GitHub): Version1.3 `main` sin builder @ `96f4f14`; Version1.4 `main` CON builder @ `dc1e964` = `2fd5341` + commit de tipografía (cherry-pick de `96f4f14` sobre `2fd5341`; el revert NO se replica). Remote local `v14` configurado hacia `aircargo-saas-Version1.4.git` (URL derivada de `origin` incluye token `ghp_...` — no exponerlo).

| File | Change |
|------|--------|
| Commit revert `c9ebfa8` (Version1.3) | **DEL** Dashboard Builder completo: `DashboardBuilderController/Servicio/FormulaEngine`, 7 DTOs (FieldDef/CalculatedField/Pivot*/Evaluate/Filter/ReportConfig), `DashboardReportEntity/Repository`, `V2__create_dashboard_report.sql`, `DashboardBuilderServiceTest`, `DashboardBuilderPanel.vue`, `FieldPicker.vue`, `dashboardReports.js`, claves i18n `db.*` (es/en ~105 claves), ruta `/api/dashboard-builder/**` de `RouteConfig`, documento de análisis `...NALISIS-DASHBOARD-BUILDER-REPORTE-CALCULABLE.md` (26 archivos, −3582 líneas). **CONSERVA**: EA type (`V3__add_ea_type.sql`), `useHeaderFilters`, fix visual DashboardView, `useIcons`, `EmptyState.vue` |
| `frontend/index.html` | Google Fonts: `Cascadia+Code:wght@400;600;700` y `Bodoni+Moda:opsz,wght@6..96,400..900` (verificados 200 OK en el endpoint CSS2; `Bodoni+FLF` no existe → 400) |
| `frontend/src/assets/main.css` | Selectores `data-font` nuevos: `cascadia` (Cascadia Code, `--font-family-weight:600`), `bodoni` (Bodoni Moda serif), `combo` (Cascadia UI + headings `h1..h4`/`.title`/`.ds-title`/`.ds-modal-title` en Bodoni Moda); body con `font-weight: var(--font-family-weight, 400)` |
| `frontend/src/utils/font.js` | `VALID = ['consolas','nerd','sans','cascadia','bodoni','combo']`, default **`combo`** |
| `frontend/src/components/layout/Header.vue` | Ciclo de fuentes ampliado: CMB → CSC → BDN → CON → NRD → SNS (FONT_ORDER/FONT_LABEL) |
| `frontend/src/components/ToastNotifications.vue` | Fuente hardcodeada `'JetBrains Mono', 'Courier New', monospace` → `var(--font-family)` |

**Verificación**: check:refs ✓ 43 SFC 0 sin resolver, eslint ✓, `npm run build` ✓, vitest **18/18** ✓. Backend compile OK con `JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto` (el Maven flatpak trae JDK viejo → "release version 21 not supported"; usar JDK 21) — EXIT=0 (export-service + gateway).

Lección git: al pushear la MISMA rama `main` a dos repos, el árbol `96f4f14` NO contiene el builder (el revert está en su historia) — para que un repo conserve contenido que otro revirtió hay que **cherry-pick solo el commit de la feature sobre el ancestro correcto**, no pushear la rama entera. Primer intento dejó V1.4 sin builder; corregido con `git branch v14-fix 2fd5341 && git cherry-pick 96f4f14 && git push -f v14 v14-fix:main`.

## Recent session changes (Sep 4, 2026 (3) — ULD form: campo "Weighed by:" nuevo + "Built by:" → "Loaded by:")

Pedido de negocio para el formulario de ULD: agregar campo **"Weighed by:"** (texto) persistente, y renombrar el campo existente **"Built by:" → "Loaded by:"** (incluida la columna de BD `built_by` → `loaded_by`).

| File | Change |
|------|--------|
| `backend/.../uldservice/entity/Uld.java` | `@Column(name="built_by")` → `loaded_by` (campo `builtBy` → `loadedBy`); **NEW** `@Column(name="weighed_by", length=100) private String weighedBy` + getters/setters |
| `backend/.../uldservice/dto/UldDTO.java` | `builtBy` → `loadedBy`; **NEW** `weighedBy` en fromEntity/toEntity + getters/setters |
| `backend/.../uldservice/service/UldServiceImpl.java` | `update` copia `loadedBy` y `weighedBy` (null-safe) |
| `backend/.../feign/dto/UldDTO.java` | Ídem (contrato feign enlace con el productor) |
| `backend/.../uldservice/.../V8__rename_built_by_to_loaded_by_add_weighed_by.sql` | **NEW** — `DO $$` guarda rename `built_by`→`loaded_by` (solo si existe) + `ADD COLUMN IF NOT EXISTS weighed_by VARCHAR(100)` + comments. (Root copy **`V59__...`** sincronizado) |
| `backend/.../loadplanningservice/dto/LoadPlanningUldDTO.java` | `builtBy` → `loadedBy` + **NEW** `weighedBy` |
| `backend/.../loadplanningservice/service/LoadPlanningServiceImpl.java` | Copia `loadedBy`/`weighedBy` del feign UldDTO |
| `backend/.../loadplanningservice/controller/LoadPlanningController.java` | Pallet sheets PDF: footer `Built By:` → **`Loaded By:`** + celda **`Weighed By:`** (reemplaza el "Time:" duplicado de Completed At) |
| `frontend/src/views/UldsView.vue` | Form: input loadedBy (label `ulds.form.loadedBy`) + **NEW** input `weighedBy` (grid de campos → `sm:grid-cols-4 xl:grid-cols-5`); `initForm()` con `loadedBy`/`weighedBy`; mapping desde backend ULD; payload update con `loadedBy: uld.loadedBy ?? null, weighedBy: uld.weighedBy ?? null` |
| `frontend/src/stores/ulds.js` + `stores/app.js` | DTO de `dispatchUld` con `loadedBy`/`weighedBy` (null-safe) |
| `frontend/src/i18n/es.js` + `en.js` | `ulds.form.{builtBy,builtByPlaceholder}` → `{loadedBy:'Cargado Por', loadedByPlaceholder:'Operador que cargó', weighedBy:'Pesado Por', weighedByPlaceholder:'Operador que pesó'}` (es) / `Loaded By/Operator who loaded`, `Weighed By/Operator who weighed` (en) |

Notas: los V3 (ULD) y V51 (raíz) históricos que crearon `built_by` quedan intactos (historia Flyway); la nueva V8/V59 hace el rename idempotente. `FeignContractSyncTest` de uld valida el contrato por reflection (ambos DTOs tienen `loadedBy`/`weighedBy` → 11/11 OK). No quedan referencias a `builtBy`/`Built By` fuera de las migraciones históricas.

**Verificación**: `compile -am` (feign + uld + load-planning + common) BUILD SUCCESS; `mvn -o -pl aircargo-uld-service -am test` **11/11** (FeignContractSync 2 + UldServiceImpl 9) — notar que el `test` standalone sin `-am` usa el jar feign del `.m2` (stale) y falla el contrato; con reactor usa `target/classes` fresco. `aircargo-load-planning-service test` BUILD SUCCESS (sin tests). Frontend: check:refs 43 SFC 0 refs, lint OK, vitest 18/18, build OK. Sin commit.

**Nota posterior (mismo día)**: en el pallet sheet PDF, la columna **Wgt (Lbs)** del breakdown de AWB quedó **vacía** por completo (fila de MAWB `<td class='r'></td>` Y fila TOTAL `<td class='r'></td>`) — antes mostraba `pcs` (fila MAWB, contador de piezas NO peso) y `gross` del ULD (fila total, redundante con la celda "Gross: X | Tare: Y | Net: Z"). Cumple "no mostrar nada si no se tiene esa información". Solo cambió `LoadPlanningController.java`; `compile -am` load-planning EXIT=0.

## Recent session changes (Sep 4, 2026 (2) — Load planning export real (vuelo/aerolínea/fecha) + aerolínea en pallet labels ULD + gridview receipts completo)

Tres mejoras pedidas sobre el gridview uniforme y el export de load planning:

**1. Gridview uniforme completado en `WarehouseReceiptsView`** — `bookings/` ya tenía filtros por header en todas las columnas y `mawbs/` quedó exento (diseño personalizado). El único gap eran las columnas **Piezas** y **Peso (kg)** de recibos:

| File | Change |
|------|--------|
| `frontend/src/views/WarehouseReceiptsView.vue` | Filtros de header en **Piezas** (popup centrado, `uniqueValues.pieces` numérico asc) y **Peso (kg)** (popup right-0, `uniqueValues.weight` numérico asc) con triggers `toggleHeaderFilter('pieces'/'weight')`; `columnFilters` ampliado con `pieces`/`weight`; `filteredMawbs` filtra por ambos tras `dest`. Helpers nuevos `displayPieces(m)` (`receiptTotals.value[m.id]?.pieces || m.pieces || 0`) y `displayWeightKg(m)` (`Math.round(Number(receiptTotals.value[m.id]?.weightKg || m.reportedWeightKg || 0))`) — referencia perezosa a `receiptTotals` declarado más abajo (OK: solo se evalúa dentro de computeds). "Todos" hardcodeado → `{{ t('common.all') }}` en los 4 popups previos (mawb/shipper/dest/status) |

**2. Pallet sheet / pallet label con la aerolínea real del vuelo (ya no "UPS" hardcodeado)** — verificado E2E que el PDF de pallet sheets del load-planning (backend) ya usa `plan.getAirlineName()` dinámico. El hueco era la **pallet label ULD** (etiqueta Zebra/PDF del diseñador), que no tenía datos de aerolínea/vuelo:

| File | Change |
|------|--------|
| `backend/.../uldservice/service/PalletLabelService.java` | Inyectado `FlightClient` (constructor + campo); `buildDataList` llama al nuevo `appendFlightAirline(data, uld)`: best-effort try/catch resuelve vuelo por `uld.getFlightId()` y aerolínea por `flight.getAirlineId()` con fallback a `uld.getAirlineId()`; añade `AIRLINE` (nombre), `AIRLINE_CODE`, `FLIGHT_NUMBER`, `FLIGHT_DATE`, `FLIGHT_ROUTE` (origin-dest) al mapa de datos de la plantilla PALLET |
| `frontend/src/utils/labelConfig.js` | FIELDS.PALLET ampliado con `AIRLINE`/'Aerolínea (nombre)', `AIRLINE_CODE`, `FLIGHT_NUMBER`, `FLIGHT_DATE`, `FLIGHT_ROUTE`; SAMPLE_DATA.PALLET con valores ejemplo — las plantillas del diseñador ahora pueden colocar aerolínea/vuelo/fecha |

**3. Load planning export por backend con vuelo/aerolínea/fecha + CSV nuevo** — antes el XLSX backend (`exportFlightLoadPlan`) arrancaba con las columnas ULD sin identificar el vuelo, y no existía CSV backend (el botón "Exportar manifiesto" del frontend generaba XLSX client-side ya con vuelo/aerolínea/fecha):

| File | Change |
|------|--------|
| `backend/.../loadplanningservice/service/LoadPlanningExportService.java` | Inyectado `FlightClient`; reescrito `exportFlightLoadPlan` con **bloque de cabecera**: fila 0 título (merger A..G, "MANIFIESTO DE ESTIBA / LOAD PLAN"), fila 1 `VUELO: {code}-{num}` | `FECHA:` | `RUTA: origin > dest` | `AEROLINEA: {code}` (estilo infoStyle azul), fila 3 header de columnas, datos desde fila 4. Helpers `resolveAirline(flight)` (best-effort) y `joinCodeNum`. **NEW** `exportFlightLoadPlanCsv`: mismo vuelo/aerolínea/fecha + columnas ULD, escape CSV con comillas (`,`/`"`/`\n`), UTF-8, títulos como línea inicial |
| `backend/.../loadplanningservice/controller/LoadPlanningController.java` | **NEW** `GET /api/load-planning/flight/{flightId}/export-manifest/csv` → `text/csv`, filename `LOAD_PLAN_FLIGHT_{8chars}.csv` |
| `frontend/src/views/LoadPlanningView.vue` | Botón **CSV** junto a "Exportar Manifiesto": `exportToCSV()` → `api.get('/load-planning/flight/{id}/export-manifest/csv', { responseType:'blob' })`, descarga con Content-Disposition; guard de vuelo seleccionado y ULDs asignados (mismo patrón que `exportPalletSheets`) |
| `frontend/src/i18n/es.js` + `en.js` | Clave `loadPlanning.exportCsv` ('Exportar CSV' / 'Export CSV') |

**Verificación**: backend offline `compile -am` (load-planning + uld + feign + common) BUILD SUCCESS; `aircargo-uld-service test` 11/11 (FeignContractSync 2 + UldServiceImpl 9); `aircargo-load-planning-service test` BUILD SUCCESS (sin tests). Frontend: check:refs 43 SFC 0 refs, lint OK, vitest 18/18, build OK. Nota: no existen tests que construyan `PalletLabelService` directamente (controller lo inyecta por Spring), por lo que el constructor nuevo no rompe nada.

**Bug reportado**: `GET /api/receipts/{id}` (ruta usada al emitir/actualizar un recibo) fallaba con `500 ClassCastException: ImmutableCollections$ListN cannot be cast to WarehouseReceiptDTO` en `WarehouseReceiptController.getById:38` (`Optional.map(ResponseEntity::ok)`).

**Causa raíz**: dos `@Service` distintos compartían el **mismo nombre de caché Caffeine `warehouse-receipts`** guardando valores de tipos distintos bajo la misma clave por defecto:
- `WarehouseReceiptServiceImpl.getAll()` — `@Cacheable("warehouse-receipts")` (clave default `SimpleKey.EMPTY`), guarda `List<WarehouseReceiptDTO>`.
- `WarehouseServiceImpl.getPieces(receiptId)` — `@Cacheable("warehouse-receipts")` (también clave default), guarda `List<ReceiptPieceDTO>`.

Al colisionar ambas listas bajo la misma clave default, el método que leía la caché recuperaba una `List` del **otro** tipo; cuando `getById` (misma caché, clave `#id`) entregaba el valor mal tipado al controller, `ResponseEntity::ok` (que espera `WarehouseReceiptDTO`) recibía una `List` → `ClassCastException`. No era problema de token/MFA: login con token válido también fallaba en esa ruta.

| File | Change |
|------|--------|
| `backend/.../warehouseservice/service/WarehouseServiceImpl.java` | **FIX** — `getPieces(receiptId)` pasó de `@Cacheable("warehouse-receipts")` a **`@Cacheable("receipt-pieces")`** (caché propia, sin colisión de tipos con la lista de recibos). `emitReceipt` y `updateReceipt` ahora usan `@CacheEvict(value = {"warehouse-receipts", "receipt-pieces"}, allEntries = true)` para invalidar ambas cachés al mutar piezas |
| `backend/.../common/cache/TypeSafeCacheManager.java` | **NEW** — `CacheManager` decorador: envuelve cada caché en `TypeSafeCache`. Defensa global contra TODA la clase de error (no solo warehouse) |
| `backend/.../common/cache/TypeSafeCache.java` | **NEW** — wrapper de `Cache` que valida el tipo del valor en `get(key, type)`: si el valor almacenado no es asignable al tipo esperado por el método `@Cacheable`, lo **desaloja** y devuelve `null` (cache-miss) en vez de dejarlo propagarse y romper el controller con `ClassCastException`. `get(key)` sin tipo no valida (no hay tipo esperado) |
| `backend/.../common/cache/CacheConfig.java` | `cacheManager()` ahora devuelve `new TypeSafeCacheManager(manager)` — protección type-safe activada en el Caffeine compartido de TODOS los servicios |
| `backend/.../common/cache/RedisCacheConfig.java` | Ídem para el modo Redis (HA): `new TypeSafeCacheManager(redis)` |
| `backend/.../common/cache/TypeSafeCacheTest.java` | **NEW** — 3 tests del guard: mismatch→miss (lista guardada donde se espera String → null + entrada desalojada, antes ClassCastException), tipo correcto→valor devuelto, manager envuelve/degela |

**Lección para el futuro**: cada tipo de valor distinto que se almacene en caché (lista vs DTO vs Optional) debe usar un **nombre de caché propio**; nunca compartir la misma caché para métodos con tipos de retorno incompatibles, porque Caffeine es type-erased (clave→Object) y cualquier colisión de clave devuelve el valor "tal cual" al método que la lee. Como segunda línea de defensa, la caché ahora es **type-safe globalmente** (TypeSafeCacheManager/TypeSafeCache): cualquier colisión futura deja de lanzar `ClassCastException` y se degrada a cache-miss silencioso.

**Verificación**: `mvn -o test -pl aircargo-warehouse-service -am` → BUILD SUCCESS (common **22** — 19 previos + 3 del guard `TypeSafeCacheTest` — + warehouse 4). Reactor completo `install -DskipTests` reconstruido y servicio reiniciado (PID nuevo 162972, Java 21, arranca en ~8s). No se pudo hacer E2E HTTP por falta de credenciales válidas (los logins con fallback devuelven 401/428 por password desconocida o MFA obligatorio) — la validación queda cubierta por los tests del guard (simulan la colisión exacta del bug y verifican que ahora devuelve miss, no ClassCastException) y el build.

## Recent session changes (Sep 2, 2026 (4) — Dashboard Builder UX: popup de campos, gráficos navegables, filtros/fields mejorados + selector de fuente global)

5 mejoras solicitadas tras el modo pivot (solo frontend; backend /evaluate y /pivot intactos y verificados en :8080): field-picker popup, gráfico mejor distribuido y navegable, mejor selección de filtros/campos, gráfico más factible (export PNG/CSV + etiquetas), y selector global de tipo de letra en todas las vistas.

| File | Change |
|------|--------|
| `frontend/src/components/FieldPicker.vue` | **NEW** — popup reutilizable para elegir campos: trigger expansible (muestra el campo actual + ⌄), panel con búsqueda, grupos por fuente (ULD/Flight/Airline/MAWB/Booking/Receipt/Scenario) expandibles/colapsables, botón "Todos" por grupo, modo single o multi-checkbox, muestra hint/unidad en tooltip. Cierra con click-fuera / Escape. Soporte Tokyo Night vía `data-theme`. Props: `fieldsBySource`, `modelValue`, `multi`, `onlyNumeric`, `placeholder`, `labelOf` |
| `frontend/src/components/DashboardBuilderPanel.vue` | **#1 popup**: pivot rows (multi), pivot column (single), pivot values-field (only numeric) y filter field ahora usan `FieldPicker` en vez de `<select>` nativo. **Panel de campos (columnas) convertido a popup dropdown** (`FieldPicker multi` enlazado a `cfg.fieldSources`): se eliminó el card expandido con todos los checkboxes agrupados por fuente (ahorró ~360px de alto → la gráfica y el config quedan visibles sin zoom; el popup agrupa por fuente con búsqueda, "Todos" por grupo y contador de seleccionados). `#2/#4 gráfico`: contenedor `overflow-x-auto`, SVG con **ancho dinámico** (`chartCanvasW = max(600, 36*2 + n*64)`) para que las barras mantengan ancho cómodo y se haga scroll horizontal con muchos puntos; toolbar con toggle de etiquetas (`showLabels`, movido v-if a `<g>` wrapper por lint), **export PNG** (`exportPng` → clona SVG, inyecta fuente actual, rasteriza a 2x canvas) y **export CSV** (`exportCsv` con BOM + escape)`; `pointsData` ahora incluye coords `x`. Se eliminaron `fieldQuery`/`filteredFieldGroups`/`selectAllGroup`/`fieldUnit`/`sourceColor` (la lógica de agrupación/búsqueda/todos vive ahora en FieldPicker) |
| `frontend/src/utils/font.js` | **NEW** — `getFont/setFont/applyFont/initFont` con `data-font` en `<html>` y persistencia `localStorage 'aircargo_font'`; valores `consolas`/`nerd`/`sans` (default consolas) |
| `frontend/src/assets/main.css` | **#5** — CSS vars `--font-family(-mono/-sans)`; `[data-font='consolas'|'nerd'|'sans']` reasigna `--font-family`; `body/.font-mono/button/input` usan `var(--font-family)` |
| `frontend/tailwind.config.js` | `fontFamily.mono` y `.sans` → `['var(--font-family)', …]` — así TODOS los `font-mono`/`@apply font-mono` de las clases `ds-*` cambian de fuente globalmente |
| `frontend/src/App.vue` | `initFont()` junto a `initTheme()` |
| `frontend/src/components/layout/Header.vue` | Botón de fuente junto al tema: cicla `CON → NRD → SNS` (`cycleFont`, `fontLabel`) con tooltip `header.fontHint` |
| `frontend/src/i18n/es.js` + `en.js` | Claves `db.pickSelected/pickOpen/pickChooseFields/pickSearch/pickClose/pickAll/pickNone/pickNoMatch/pickOn/pickOff/searchFields/points/chartLabels/chartPng/chartCsv/pivotClear` + `header.fontHint` |
| `frontend/scripts/check-sfc-refs.mjs` | GLOBALS ampliado con `XMLSerializer` (API nativa usada en exportPng) |
| `frontend/src/views/DashboardView.vue` | **FIX scroll de la gráfica** — el tab builder ahora vive en `<div class="flex-1 min-h-0 overflow-y-auto pr-1">`: el raíz usa `.ds-page` (`h-screen overflow-hidden`), que RECORTABA el contenido vertical del Builder (la gráfica quedaba inalcanzable sin scrollbar). El wrapper da volumen acotado + scroll propio para ver la gráfica sin zoom. |
| `frontend/src/components/DashboardBuilderPanel.vue` | **FIX gráfica en modo pivot** — la gráfica antes solo existía en flat (`v-if="result.rows"` y todo el pipeline `pointsData/numericColumns/chartDataCol/chartCanvasW` leía `result.value`, que es `null` en pivot). Ahora `chartColumns`/`chartRows` unifican ambas fuentes (flat usa `result`; pivot construye filas `{ rowField: key, measureLabel: cell[__total] }` desde `pivotResult`) → la gráfica se renderiza en AMBOS modos. **Auto-scroll** `scrollToChart()` tras `runEval` lleva la gráfica a la vista (`scrollTo` al scroller `overflow-y-auto`/`main.overflow-auto`). |

**Verificación**: `check:refs` 44 SFC 0 refs, `lint` OK, `build` OK, `vitest` 15/15.

**Problema reportado**: la tabla del builder seguía "sin información" pese a que el backend ya devolvía filas (fix de tabla base de la sesión anterior). Además se pidió un **reporte de consulta abierto tipo pivot table**: que el usuario elija los campos (filas/dimensiones + medidas) y la app calcule la agregación. Se implementó un modo PIVOT completo (filas + medidas + desglose por columna opcional) como segunda vía del builder, además del modo tabla plana.

**Modelo de pivot** (estilo Klipfolio/DashThis): base de origen (`baseSource`), **filas** = 1+ dimensiones que agrupan (tupla), **valores** = 1+ medidas con agregación SUM/AVG/MAX/MIN/COUNT, y **columna** opcional cuyos valores distintos se convierten en grupos de columnas. Cada celda = agregación de una medida restringida a (fila × columna). Gran total por (columna × medida). Reusa las filas crudas de `buildRawRows` (misma unión ULD→Flight→Airline→MAWB→Booking→Recibo) + filtros `chartConfig.filters`.

| File | Change |
|------|--------|
| `backend/.../exportservice/service/DashboardBuilderService.java` | **Refactor** — extraída `buildRawRows(String base, …)` (el switch de tabla base de paso "3" de `evaluate`, reutilizable); el `evaluate` la invoca con `baseSourceOf(cfg)`. **NEW** `publish ... pivot(PivotConfig)`: carga las mismas entidades que `evaluate`, agrupa por la tupla de `rows`, desglosa por `column` (valores distintos → `colGroups`), agrega cada `values[i]` por (fila × columna) con `agg(...)` (SUM/AVG/MAX/MIN/COUNT), ordena filas por su total global, produce `PivotResult`. Helpers `agg`, `mergeTotals`, `aggLabel`, `globalTotalOf`. Filtros: `filtersOf` refactorizado a `filtersOfChart(Map)` (sobrecarga para ReportConfigDTO y PivotConfig). Índice de celdas = `colGroup*measureCount + measureIdx` (`PivotResult.cellIndex`) |
| `backend/.../exportservice/dto/PivotConfig.java` | **NEW** — record `(String baseSource, List<String> rows, List<PivotValue> values, String column, Map chartConfig)` |
| `backend/.../exportservice/dto/PivotValue.java` | **NEW** — record `(String field, String agg)` con agg default SUM (normaliza a mayúsculas) |
| `backend/.../exportservice/dto/PivotResult.java` | **NEW** — record `(baseSource, rowFields, colGroups, measures, List<PivotRow> rows, List<Object> totals)` + `cellIndex(col,measure,mCount)` |
| `backend/.../exportservice/dto/PivotRow.java` | **NEW** — record `(List<String> key, List<Object> cells)` |
| `backend/.../exportservice/controller/DashboardBuilderController.java` | **NEW** `POST /api/dashboard-builder/pivot` → `service.pivot(PivotConfig)` |
| `backend/.../exportservice/service/DashboardBuilderServiceTest.java` | **+2 tests (total 11)**: `pivot_groupsByRowDimensionsAndAggregatesMeasures` (agrupa por MawbStatus, SUM=16 de piezas en RECEIVED de 2 mawebs) y `pivot_columnPivotCreatesColumnGroups` (column=MawbStatus → colGroups RECEIVED/BOOKED, celda por vuelo) |
| `frontend/src/components/DashboardBuilderPanel.vue` | **Modo pivot** — botón toggle `Modo Pivot/Modo Tabla` en el toolbar (`toggleMode`). Card "Configuración de tabla dinámica": selectores de **Filas** (1+ con botón añadir/quitar), **Columnas** (opcional), **Valores** (campo numérico + agregación, 1+). `runEval` bifurca: en pivot envía `POST /pivot` con `{baseSource, rows, values, column, chartConfig.filters}` (payload `rows=...filter(Boolean)`); en flat conserva `/evaluate`. **Resultado** renderiza matriz pivot: cabecera doble (fila de grupos de columna con `colspan=measures.length` sobre fila de medidas) + totales. Persistencia: `chartConfig.mode` + `chartConfig.pivot={rows,column,values}` en save/load. Helpers `labelOf`, `fmtPivot`, `numberFields`, refs `mode/pivotResult/pivotRows/pivotColumn/pivotVals` |
| `frontend/src/api/dashboardReports.js` | **NEW** `pivot(cfg)` → `POST /dashboard-builder/pivot` |
| `frontend/src/i18n/es.js` + `en.js` | Claves `db.pivot{Mode,FlatMode,Cfg,Rows,PickRow,AddRow,Cols,NoCol,ColHint,Vals,PickField,AddVal,ValHint,Result,All,Total}` |

**Verificación**:
- Backend: reactor export → **11/11 tests** BUILD SUCCESS. Jar reconstruido (`install`) y desplegado en :9099.
- Frontend: `check:refs` 0, `lint` OK, `build` OK, `vitest` 15/15.
- **E2E real vía gateway** (`baseSource=mawb`, BD real mawb=24/booking=24/receipt=2/uld=0): `rows=[MawbStatus]` value SUM(MawbPieces) → BOOKED 398, RECEIVED 104, total 502; `rows=[FlightNumber]` + `column=MawbStatus` → matriz `{0403: [398,104]}`; 2 medidas + column + filtro `AwbNumber contains 58` → `cells=[BOOKED·Sum=172, BOOKED·Avg=3495, RECEIVED·Sum=4, RECEIVED·Avg=3969]` (índice colGroup×mCount+measure ✓); **filas anidadas** `rows=[AirlineCode,MawbStatus]` → key `["UPS","BOOKED"]/["UPS","RECEIVED"]`.

Nota: la persistencia de `mode`/`pivot` viaja dentro de `chartConfig` (el controller ya guarda `chartConfig` como JSON), así que NO requirió migración de BD. El modo flat sigue intacto (catálogo de campos + fórmulas + filtros + gráficos). Builder sin comitear; SIN push a GitHub.

## Recent session changes (Sep 2, 2026 (2) — Dashboard Builder: tabla base de filas seleccionable)

**Bug reportado por el usuario**: evaluar un reporte generaba SOLO los headers (columnas) pero CERO filas de datos. **Causa raíz diagnosticada**: el motor del builder construía una fila por `uld_awb` (única fuente de filas), y en la BD real `uld`/`uld_awb` están en **cero** (solo MAWBs/Bookings/Recibos populados) → sin `uld_awb` no había filas que renderizar, sin importar cuántos MAWBs existieran.

**Fix — dataset de origen ("tabla base") parametrizable** (`cfg.baseSource`), estilo Klipfolio/DashThis/Looker Studio: el builder ahora emite **una fila por registro de la tabla base elegida**, rompiendo la dependencia exclusiva de `uld_awb`. Cada base resuelve sus columnas con las mismas uniones (vuelo/aerolínea desde el MAWB o ULD; booking por mawbId/awbNumber; recibo no-superseded por mawbId).

| File | Change |
|------|--------|
| `backend/.../exportservice/service/DashboardBuilderService.java` | **Reescrito el paso de filas** — carga TODAS las entidades en mapas (`uldById`, `flightById`, `mawbById`, `bookingBy*`, `receiptByMawbId`, `airlineById`) vía `findAll` (antes `findById` por ULD-AWB, solo alcanzaba ULDs ligados a piezas). Índices `awbByUld`/`awbByMawb` por piezas. `baseSourceOf(cfg)` lee `cfg.baseSource()` (fallback a `chartConfig.baseSource`, default `uld-awb`). `switch` por base: `mawb`/`booking`/`receipt` → `buildRowMawb(m,b,r,pieces,…)` (resuelve vuelo/aerolínea por `m.getFlightId()`/`m.getAirlineId()`, piezas = suma de ULD-AWB del MAWB); `flight` → `buildRowFlight`; `uld` → `buildRowUld`; `uld-awb` → `buildRowAwb` (comportamiento histórico). Helpers `baseFields` (columnas ULD+vuelo+aerolínea), `fillMawb/fillBooking/fillReceipt` compartidos |
| `backend/.../exportservice/dto/ReportConfigDTO.java` | **NEW campo** `String baseSource` (mawb\|booking\|receipt\|flight\|uld\|uld-awb) en el record |
| `backend/.../exportservice/service/DashboardBuilderServiceTest.java` | **+2 tests (total 9)**: `evaluate_baseSourceMawbReturnsRowsEvenWhenUldAwbEmpty` (uld_awb vacío + base=mawb → 2 filas reales, piezas 0) y `evaluate_baseSourceBookingGroupsByFlight` (1 fila por booking, vuelo resuelto del MAWB, agrupa por FlightNumber). **FIX en tests previos**: los 6 tests que usaban `uldRepo/flightRepo.findById` ahora stubean `findAll` (el motor ya no usa `findById`); constructor del DTO migrado a helper `cfg(...)` por el nuevo campo |
| `frontend/src/components/DashboardBuilderPanel.vue` | **NEW selector "Tabla base (filas)"** en el toolbar (uld-awb/mawb/booking/receipt/flight/uld); `cfg.baseSource` default `'uld-awb'`, incluido en evaluate/save (se envía por el spread de `cfg`), restaurado en `loadReport` con fallback `'uld-awb'` |
| `frontend/src/i18n/es.js` + `en.js` | Claves `db.baseSource` + `db.base{UldAwb,Mawb,Booking,Receipt,Flight,Uld}` |

**Verificación**: reactor export **9/9 tests** BUILD SUCCESS (los 7 previos adaptados + 2 nuevos); frontend check:refs 43 SFC 0 refs, lint OK, build OK, vitest 15/15. **E2E real vía gateway** (auth:9092 + export:9099 + gateway:8080, token HS512 real): BD `mawb=24, booking=24, receipt=2, flight=2, uld=0, uld_awb=0` → `evaluate` **sin baseSource → 0 rows (bug reproducido)**; `baseSource=mawb` → **24 filas** (sample `406-05857585 / MawbPieces 12 / FlightNumber 0403 / UPS`); `baseSource=booking` → 24 filas; `baseSource=receipt` → 2 filas; `baseSource=flight` + `dimension=AirlineCode` → 1 fila UPS con `MaxPayloadKg=90000` (commit agregado).

Lección E2E: en esta máquina el arranque manual de servicios tras `./start-all.sh` se hace con `setsid nohup java -jar … </dev/null >log 2>&1 & disown` — sin `setsid` el timeout del runner mata el proceso hijo. La BD real tiene los ULDs/piezas en cero por diseño operativo (los ULDs se registran en el módulo ULDs), por eso la tabla base es obligatoria para ver datos.

## Recent session changes (Sep 2, 2026 — Dashboard Builder: agregaciones de fórmula + Top N + motor de gráficos)

Mejora del Dashboard Builder tras análisis. Dos debilidades principales resueltas: (1) los totales ignoraban la agregación de cada columna calculada, y (2) el gráfico era una sola barra auto-detectada sin configuración. Además el selector "Top N" del frontend se enviaba pero **el backend lo ignoraba**.

| File | Change |
|------|--------|
| `backend/.../exportservice/service/DashboardBuilderService.java` | **Totales con agregación** — `totalsFor` ahora respeta `formulas[i].aggregate` (SUM default / AVG / MAX / MIN / COUNT): `Map<String,String> aggByCol` construido desde las fórmulas y aplicado por columna en la fila TOTAL (antes SUM plana siempre). **Top N real** — `topNOf(cfg)` + `chartYOf(cfg, rows)` + `doubleOrZero()`; tras aplicar columnas calculadas, si `chartConfig.topN>0` ordena las filas por el eje Y (chart.y o primera columna numérica sin `__`) descendente y recorta a las N primeras. OJO: al agrupar, `groupBy` NO copia `__uldId`/`__flightId` al mapa agregado (las claves `__` no se propagan), por lo que `chartYOf` recae en la primera numérica del orden de inserción (p.ej. ULD `TareLbs`, no `GrossLbs`) — por eso el front **siempre envía `y`** explícito en `chartConfig`. |
| `frontend/src/components/DashboardBuilderPanel.vue` | **Motor de gráficos configurables** — selector `chartType` (bar/line/area/pie), `chartX` (eje categorías: columnas no numéricas) y `chartY` (eje magnitud: columnas numéricas, con opción "(auto: primera numérica)"). SVG renderiza por tipo: barras con etiquetas + eje X rotado −30°, línea/área con path + puntos (`seriesPath`/`seriesPoints`), pastel con `arc()` slices + **leyenda con %** y paleta `PALETTE` de 10 colores. Ticks "nice" (`niceMax` con log10 → 4 gradaciones redondas + sufijo `k` para ≥1000). `gridTicks`/`yAt`/`numericColumns`/`xCandidates`. `chartType`/`x`/`y` se persisten en `chartConfig.type/x/y` (runEval + saveReport) y se restauran en `loadReport`. |
| `frontend/src/i18n/es.js` + `en.js` | Nuevas claves `db.chart{Type,X,Y,AutoY,Bar,Line,Area,Pie,Hint}`. |
| `backend/.../service/DashboardBuilderServiceTest.java` | 2 tests nuevos (+5 existentes = 7): `evaluate_totalsRespectFormulaAggregation` (AVG de `[Pieces]*2` → media de (10,10)=10, mientras `Pieces` usa SUM=10) y `evaluate_topNKeepsHighestRows` (topN=1 por `y=GrossLbs` → grupo 5Y2000 con Gross 300; **requiere `y` explícito** porque sin `__flightId` en filas agrupadas el auto-detecte cae en `TareLbs` 20 igual en todos → empate estable). |

**Verificación**: reactor export `BUILD SUCCESS` (7/7 tests); frontend `check:refs` 43 SFC 0 refs, `lint` OK, `build` OK, `vitest` 15/15. **E2E real vía gateway** (token HS512 acuñado con el `JWT_SECRET` del `.env`, clave UTF-8 cruda): con datos temporales (ULD-A UPS gross 2000 + ULD-B FDX gross 4000, 4 `uld_awb`) → evaluate agrupado por AirlineCode con `formulas=[{column:PiecesX2, expression:[Pieces]*2, aggregate:AVG}]`, `chartConfig.topN=1, y=GrossLbs` → **1 fila FDX 4000** (topN respeta eje Y); sin topN → 2 grupos UPS(2000,6,12) y FDX(4000,10,20); **totales** `GrossLbs=6000` (SUM), `Pieces=16` (SUM), **`PiecesX2=16` (AVG de 12 y 20)** — la agregación AVG se distingue del SUM (que daría 32). Datos de prueba limpiados (`uld_awb` → 0).

Lección E2E: la clave del `.env` es `JWT_SECRET` UTF-8 cruda de 64 bytes; para acuñar un token de acceso basta firmar HS512 (`alg:HS512`) con la clave UTF-8, subject=userId, claims `role/airlineId/email/fullName/tokenType=access/iat/exp`. El export-service rechazaba el token cuando se lanzaba **sin** cargar `.env` (secret distinto del entorno) — relanzar con `set -a; source ./.env` resuelve el 401.

## Recent session changes (Aug 30, 2026 (2) — MFA OBLIGATORIO con enrolamiento forzado en el login)

**Requisito de negocio**: todos los usuarios deben tener MFA configurado antes de operar. Antes MFA era opcional (`mfaEnabled`), ahora es **obligatorio** (`app.mfa.mandatory=true`): un usuario con credenciales válidas pero sin MFA NO recibe token — se le fuerza a enrolarse en el login.

**Flujo de enrolamiento (sin sesión previa)**:
1. `POST /api/auth/login` → password válida + `mfaEnabled=false` → **HTTP 428** `{mfaEnrollmentRequired:true, enrollToken, email, message}` (ERROR, el login no emite cookies).
2. `enrollToken` = JWT `tokenType="enroll"`, TTL **15 min** (`ENROLL_TOKEN_MS`, un solo uso — se revoca al habilitar).
3. `POST /api/auth/mfa/enroll/setup` `{enrollToken}` (público, token por BODY) → `{secret, otpAuthUrl, email}`.
4. `POST /api/auth/mfa/enroll/enable` `{enrollToken, secret, totpCode}` (público, body) → verifica TOTP, `enableMfa()`, revoca el enrollToken, audita `MFA_ENROLLED` → `{enrollSuccess:true, email}`.
5. Frontend hace **re-login** con email+password+totpCode para obtener la sesión real.

**Puertas aplicadas**:
- `LoginCommandHandler` ⇢ `LoginOutcome.Status.MFA_ENROLLMENT_REQUIRED` → 428.
- `SetPasswordCommandHandler` + `AuthController.setPasswordByToken` → si mandatory && sin MFA → 428 enroll **sin emitir JWT** (nadie logra sesión sin MFA).
- **Seguridad del enrollToken**: `JwtAuthFilter` (common) y `JwtGatewayFilter` ahora validan `tokenType` — solo aceptan `access`/`service`; los `enroll`/`refresh` jamás funcionan como Bearer en ningún servicio (verificado E2E: enrollToken → 401).

| File | Change |
|------|--------|
| `common .../auth/JwtUtil.java` | `generateEnrollToken(userId, role, email, fullName)` + `ENROLL_TOKEN_MS = 15min` |
| `common .../auth/JwtAuthFilter.java` | Check `tokenType` (solo `access`/`service`) — protege TODOS los servicios del enrollToken |
| `gateway .../filter/JwtGatewayFilter.java` | Ídem + `/api/auth/mfa/enroll/**` en `PUBLIC_PATHS` |
| `auth .../command/LoginOutcome.java` + `PasswordOutcome.java` | `MFA_ENROLLMENT_REQUIRED` |
| `auth .../command/LoginCommandHandler.java` | Gate `mfaMandatory` (inyectado) → si `mfaMandatory && !mfaEnabled` → enrollToken |
| `auth .../command/SetPasswordCommandHandler.java` | Gate `mfaMandatory` → devuelve MFA_ENROLLMENT_REQUIRED si falta |
| `auth .../controller/AuthController.java` | Mapeo 428 + `POST /mfa/enroll/setup` y `/mfa/enroll/enable` (token por body) + `isValidEnrollToken()`/`parseSubject()` + gate en `setPasswordByToken` |
| `auth application.properties` | `app.mfa.mandatory=true` |
| `auth application-test.properties` | `app.mfa.mandatory=false` (tests previos intactos) |
| `auth MfaMandatoryIntegrationTest.java` | **NEW** — 3 tests: flujo completo enroll→setup→enable→re-login con TOTP, 428 sin enrolamiento, 428 en set-password sin MFA |
| `frontend src/api/auth.js` | `mfaEnrollSetup(enrollToken)`, `mfaEnrollEnable(enrollToken, secret, totpCode)` |
| `frontend src/views/LoginView.vue` | **Nuevo step `mfa-enroll`**: QR (api.qrserver.com), secret, input código; en `handleLogin` detecta `data.mfaEnrollmentRequired`; tras habilitar hace re-login con TOTP |
| `frontend src/i18n/es.js` + `en.js` | Claves `login.mfaEnroll.*` |
| `frontend src/components/layout/Sidebar.vue` | **Draft en logout MANUAL**: `handleLogout` ahora captura formularios (`captureForms`+`saveDraft`+`setReturnTo`) antes de `auth.logout()` — mismo patrón que `useIdleLogout` |

**Verificación**: common 19 + auth **33** (30 previos + 3 MFA) + gateway 3 BUILD SUCCESS; frontend lint/check-refs/build/vitest 15 OK. **E2E real vía gateway**: login `jsantos@rannik.com` sin MFA → 428 enroll → setup (secret TOTP) → enable (código verificado) → re-login con TOTP → JWT+refresh+cookies ✓ → BD: `mfa_enabled=t, mfa_secret` cifrado ✓ → enrollToken como Bearer en `/api/flights/list` → **401** (gate rechaza) ✓. Usuario `jsantos@rannik.com` quedó con MFA habilitado (secret `3332RESPZ2NOX6TEMISPCPO2IR3ZJRNZ`) — si se necesitara resetar su MFA, borrar `mfa_secret` + `mfa_enabled=false` en BD.

**Nota para tests**: al generar códigos TOTP en Java, `DefaultCodeVerifier` pasa `period = floorDiv(now,30)` al generador (NO el epoch crudo) — en tests usar `new DefaultCodeGenerator().generate(secret, new SystemTimeProvider().getTime() / 30)`.

## Recent session changes (Aug 30, 2026 — Restauración de BD desde Settings + recuperación a prueba de error)

**Problema de fondo resuelto y documentado**: la BD local solo tenía datos de seed (20 usuarios de prueba, 0 MAWBs/Bookings) porque los datos reales vivían en EC2. Al restaurar el dump de EC2, Flyway rompía con checksum mismatch (historial EC2 generado con jars 1.2 vs jars 1.3), abortando el arranque de los servicios. Fix: `flyway:repair` de las 7 tablas de historial + reconstrucción de jars. **Evidencia E2E**: login real (`jsantos@rannik.com` ADMIN) → mawbs 24, bookings 24, flights 2, users 20, receipts 2, airlines 10.

**NUEVA FUNCIONALIDAD — Restaurar BD desde Settings (URL de nube o copia local)**: el admin puede restaurar la BD completa desde la UI (Settings → Backups → "Restaurar base de datos"), sin SSH. Sirve para que las actualizaciones del sistema nunca comprometan la integridad: antes de cada deploy se genera un punto de restauración y, si algo falla, se vuelve atrás con un clic.

| File | Change |
|------|--------|
| `scripts/db-restore.sh` | **NEW** — restauración idempotente desde `--file <ruta.dump>` o `--url <https://...>` (URL descargada con curl, solo http/https, timeout 10min). **Siempre crea primero un backup de PROTECCIÓN** de la BD actual vía `db-backup.sh pre-restore` (los datos actuales nunca se pierden: "restore a prueba de error"). Valida magic bytes `PGDMP` (rechaza formatos corruptos/erróneos). Restaura con `pg_restore --clean --if-exists --no-owner --no-privileges`. **Clasifica el error benigno `transaction_timeout`** (dump generado con pg_dump ≥17 vs PG 16): si es el ÚNICO error → éxito real; cualquier otro `ERROR` → fallo reportado. Verifica conteos post-restore y registra la acción en `backup_history` (tipo `RESTORE`, SUCCESS/FAILED) + `rollback.log`. Exige el `.env` raíz (POSTGRES_HOST/PORT/DB/USER/PASSWORD) — probado E2E contra BD temporal y contra la BD real vía gateway |
| `backend/.../authservice/dto/BackupDTOs.java` | `RestoreRequest` (source `local`/`url`, filePath, url) + `RestoreResult` (success, message, dumpPath, exitCode) |
| `backend/.../authservice/controller/BackupConfigController.java` | **NEW** `POST /api/backup/restore` (ADMIN/SUPER_USER): valida la fuente (anti-SSRF: URL debe ser http/https, `file://` rechazado), localiza `scripts/db-restore.sh` subiendo directorios desde cwd, ejecuta por ProcessBuilder con salida capturada; devuelve `RestoreResult` con mensajes ✅/⚠️/❌. `resolveScript(name)` genérico (backup + restore). Gateway ruta `/api/backup/**` ya cubría el nuevo endpoint; SecurityConfig idem |
| `frontend/src/api/backups.js` | `restore(payload)` → `POST /backup/restore` |
| `frontend/src/views/SettingsView.vue` | Sección **"Restaurar base de datos"** en el tab Backups: radio local/URL, input de ruta o URL con `<Enter>` para enviar, botón rojo "Restaurar BD" deshabilitado si falta la fuente/in-progress, diálogo de confirmación destructivo (useConfirm, danger) en es/en, resultado inline (verde ✅ / rojo ❌ con dumpPath), auto-refresh del historial. Backup history muestra tanto backups como restores (columna tipo `RESTORE`) |
| `frontend/src/i18n/es.js` + `en.js` | Claves `settings.backups.restore*` (title, help, local, url, filePath, urlInput, btn, restoring, safety, confirm*, done, failed) |

**Procedimiento RECOMENDADO para despliegues seguros** (documentado también en la UI):
1. `Settings → Backups → Crear backup ahora` (o `./scripts/db-backup.sh pre-deploy`), o confiar en el timer diario + el backup de protección automático que crea cualquier restore.
2. Desplegar la nueva versión.
3. Si algo salió mal → `Settings → Backups → Restaurar BD` con el dump de la nube o local. El restore crea un backup de protección del estado post-deploy antes de sobrescribir, así que siempre se puede volver también A delante.
4. Tras el restore, reiniciar el stack para limpiar cachés/pools: `./start-all.sh --skip-build` (o al menos los servicios afectados). Las migraciones Flyway que ya estén aplicadas en el historial del dump NO se re-ejecutan (checksums intactos en los dumps post-repair).

**Punto de restauración garantizado**: `backups/aircargo_bd_v13_restaurada_20260830-221839.dump` (dump post-repair, congelado tras la corrección de checksums — calza con jars 1.2/1.3). Restore de un comando sin UI:
```sh
pg_restore -h 127.0.0.1 -U aircargo_user -d aircargo --clean --if-exists --no-owner --no-privileges \
  backups/aircargo_bd_v13_restaurada_20260830-221839.dump
```
(El aviso `transaction_timeout` es BENIGNO: pg_dump de servidor nuevo emite `SET transaction_timeout=0` que PG 16 ignora; `db-restore.sh` lo reconoce y no lo cuenta como fallo.)

**Estado de la BD local (verificado post-restore)**: app_user 20, airline 10, site 4, mawb 24, hawb 0, booking 24, warehouse_receipt 2, uld 0. Flyway: `flyway_schema_history_auth` (7 filas: 0,18-23), `_flight` 3, `_booking` 3, `_mawb` 4, `_warehouse` 4, `_uld` 8, todas success=true; notification/export usan la tabla default `flyway_schema_history`. **Cookies de sesión activas** (aircargo_at/aircargo_rt sin Max-Age = session cookie, cambios compilados en jars).

Lección clave de la sesión: al restaurar dumps producidos con jars de otra versión, los servicios fallan al arrancar por checksum mismatch de Flyway — el fix es `mvn flyway:repair` (o restaurar el dump post-repair que ya los trae corregidos), NUNCA borrar tablas de historial. Usuarios sin password (login directo): `jsantos@rannik.com`, `jsantos`; con password: admin@aircargo.com, dchestaro@rannik.com, esantana@rannik.com, emmanuelsantanasolano@gmail.com, manolovprimes01@gmail.com, mmejia@rannik.com.

## Recent session changes (Aug 29, 2026 — Portabilidad multi-SO: Fedora/Ubuntu/Arch + Windows 11)
Auditoría completa para que el proyecto corra sin tocar código en cualquier Linux + Windows (WSL2). No se modificó código Java — todo son scripts bash, compose y docs. Verificado: `bash -n` x6, `docker compose config` x3 (infra+services+observability) con exit 0 y SIN warnings, frontend lint/guard 39 SFC 0 refs/tests 15/vitest 15, build OK.

| File | Change |
|------|--------|
| `start-all.sh` | **Prerrequisitos multi-distro** — nueva función `pkg_hint(jdk\|node\|docker\|postgres\|rabbitmq)` que muestra el comando correcto según `PKG_MGR` detectado (apt/dnf/pacman) en los mensajes de error; `detect_pkg_mgr()` best-effort. **`find_pg_bins()`** — detecta `initdb`+`pg_ctl` en PATH **o** en `/usr/lib/postgresql/*/bin` (Debian/Ubuntu los NO ponen en PATH vs Fedora/Arch que sí); usado tanto en el bloque "ceder :5432 a Docker" como en el fallback nativo (antes `pg_ctl`/`initdb` crudos → fallo silencioso en Ubuntu) |
| `start-backend.sh` | Logs de los 10 servicios: `/tmp/<svc>.log` → **`$LOG_DIR`** (default `~/aircargo-logs`), consistente con `start-all.sh`/rotación 10MB-14d |
| `aircargo-env.sh` | Candidatos Maven ampliados: + `/usr/share/maven/bin/mvn` (Debian/Fedora), + Homebrew (`/opt/homebrew/opt/maven`, `~/homebrew/opt/maven`) — útiles en distros donde Maven no queda en PATH |
| `docker/docker-compose.{infrastructure,services,observability}.yml` | Eliminado `version: '3.8'` obsoleto (warning de Compose v2 en cualquier SO). En `services.yml` seguía el fix de indentación de la sesión `start-all.sh` (POSTGRES_DB anidado rompía el YAML entero) |
| `README.md` | Requisito Node corregido: 20.19+ (no 18+) + enlace a la guía por SO |
| `Documents/INSTALACION-POR-SO.md` | **NEW** — guía de instalación: requisitos comunes (JDK 21, Maven autodetectable, Node 20.19+, Docker o nativos), pasos exactos por Fedora (dnf) / Ubuntu (apt) / Arch (pacman), Windows 11 (**WSL2 recomendado** — Git Bash/MSYS2 NO soportado: sin `/dev/tcp` para `port_up`, sin `ss`/`pkill`/`pg_ctl`), backups/rollback y troubleshooting con fixes reales (PRECONDITION_FAILED rabbit, 401 invalid secret, 403 masivos) |

Decisiones de diseño de portabilidad (documentadas en la guía): el datadir nativo postgres es `.local-pg/` dentro del repo (gitignored), socket en `/tmp`; RabbitMQ nativo cae a `~/.local-rabbitmq/` y en Arch el Erlang del sistema lo rompe → Docker es el camino universal (imágenes `postgres:16-alpine`, `rabbitmq:4.0-management-alpine`, `redis:7-alpine`, Temurin 21 en los 10 Dockerfiles backend + node/nginx en el frontend). En Windows NO correr los scripts en Git Bash — los puertos 5173/8080 llegan a `localhost` automáticamente desde WSL2.

## Recent session changes (Aug 26, 2026 — Backups offsite + Redis HA opcional + auditoría sin duplicados)
Cierra 4 debilidades de la tabla de análisis. **DPA/Política de Privacidad sigue con `[CORCHETES]` a la espera de datos reales del cliente (razón social/RNC/contacto) — NO rellenar por cuenta propia.** Verificado: compile + tests common 19 / gateway 3 OK.

| File | Change |
|------|--------|
| `scripts/db-backup.sh` | **OFFSITE** — tras cada dump exitoso sincroniza best-effort según `BACKUP_OFFSITE_TARGET`: prefijo `rsync:` (disco externo/NAS montado, espejo con retención local) o `rclone:` (nube; requiere `rclone config`). Nunca falla el backup local; avisos ⚠ si falta la herramienta o el montaje |
| `.env.example` | Nuevas vars: `BACKUP_OFFSITE_TARGET`, `REDIS_HOST/PORT`, `RATE_LIMIT_USE_REDIS` |
| `backend/aircargo-common/pom.xml` | `spring-boot-starter-data-redis` (compile — cliente lazy heredado por todos los servicios; sin Redis en marcha no conecta ni afecta arranque) |
| `common .../cache/RedisCacheConfig.java` | **NEW** — CacheManager Redis (`spring.cache.type=redis`): JSON serializer (sin exigir Serializable), TTL `spring.cache.redis.time-to-live` (default 300s; conservar TTL por servicio: export 60s, load-planning 120s, auth 600s vía env) |
| `common .../cache/CacheConfig.java` | Ahora condicionado a `spring.cache.type=caffeine` (matchIfMissing); `none` → Boot auto-configura NoOpCacheManager. Selección Caffeine/Redis/None explícita |
| gateway `pom.xml` | `spring-boot-starter-data-redis-reactive` |
| gateway `.../filter/RateLimitFilter.java` | **Reescrito** — backend REDIS (`app.gateway.rate-limit.use-redis=true`): ventana fija 1 min compartida entre réplicas (`INCR rl:{email}:{epochMinute}` + EXPIRE 70s), fail-open si Redis cae; default sigue in-memory resilience4j (correcto con 1 réplica) |
| gateway + 9 × `application.properties` | `management.health.redis.enabled=false` en los 10 — el cliente Redis es lazy y NO debe bajar `/actuator/health` cuando Redis está apagado (modo default) |
| `docker/docker-compose.infrastructure.yml` | Servicio `redis:7-alpine` (AOF, 256mb LRU, healthcheck ping) — OPCIONAL: solo para HA |
| `common .../security/ObservabilityConsistencyTest.java` | Guard nuevo **O3**: cada application.properties debe tener `management.health.redis.enabled=false` |
| `common .../audit/AuditService.java` | **Sin doble registro** — eliminado el publish AMQP "audit.log" (la copia de notification duplicaba cada evento). Única vía: INSERT en `audit_log` compartida; si el INSERT falla → log WARN, nunca lanza. Condición `@ConditionalOnClass(JdbcTemplate)` |
| notification `NotificationEventListener` | Handler `onAuditLog` eliminado (era el consumidor duplicado) |
| notification `RabbitConfig` | Binding `audit.log` eliminado (con comentario del motivo) |
| notification `{entity/AuditLog, repository/AuditLogRepository, config/AuditRetentionJob}` | **DELETED** — la tabla `notification.audit_log` ya no tiene productor ni lector |
| notification `V3__drop_notification_audit_log.sql` ≡ raíz `V50` | DROP TABLE `notification.audit_log` |
| `common .../event/AuditLogEvent.java` | **DELETED** — record sin consumidores tras quitar el publish |
| `common .../audit/AuditServiceTest.java` | Reescrito al comportamiento single-write (5 tests: persiste 1 registro, INSERT fallido no lanza, sin BD no lanza, IP pseudonimizada, details sanitizado) |

Lección Maven: esta máquina NO tiene mvn en PATH ni SDKMAN — usar `export MAVEN_BIN=/var/lib/flatpak/app/com.jetbrains.IntelliJ-IDEA-Community/x86_64/stable/active/files/plugins/maven-plugin/lib/maven3/bin/mvn`. Deps nuevas (data-redis/reactive) requieren un primer `dependency:resolve` ONLINE antes de compilar `-o`.

## Recent session changes (Aug 28, 2026 — Guard estático anti-runtime-errors terminado + 3 bugs reales encontrados)

Guard `check-sfc-refs.mjs` terminado y **integrado en `npm run lint` y `npm run build`** (ambos ahora anteponen `npm run check:refs`). Analiza los 39 SFC y cada archivo pasado por el scanner produce 0 referencias no resueltas salvo casos reales. **El guard encontró y permitió corregir 3 bugs reales de frontend** que ni ESLint ni el build detectaban (regla de oro: solo el runtime lo notaría).

| File | Change |
|------|--------|
| `frontend/scripts/check-sfc-refs.mjs` | **Guard terminado** — `candidates()` devuelve `{name,index}` (índice real por token); `lineOf()` con offset del bloque script (línea absoluta del archivo); `stripRegexLiterals()` nueva (quita `/.../flags` con conteo de escapes y clases, heurística de contexto para no confundir división); `extractDefinitions` reescrita: imports (default `api from`/named `{a,b}`/namespace `* as XLSX`/mixtos), declaradores múltiples (`let pcs=0, gross=0`), destructuring en params de arrow (`([value,count]) =>`), `for (const [,positions] of ..)`, arrows de un solo param con char previo significativo (fix del bug: truncar 12 chars podía cortar un string `'\n'` y dejar `stripped="\n"`), catch/for. Líneas de template calculadas vs el archivo real (`src.slice(0, templateStart+idx)`), no relativas al contenido. Exit 0 = limpio; exit 1 = hay issues (gate de lint/build) |
| `frontend/package.json` | `"check:refs": "node scripts/check-sfc-refs.mjs"`; `lint` y `build` ahora corren `npm run check:refs` primero |
| `src/views/LoginView.vue:294` | **BUG REAL #1** — el import de `@/utils/formDraft` tenía `{ popReturnTo, loadDraft, restoreForms }` pero el código llama `clearDraft()` → `ReferenceError` en runtime (ruta de restauración de drafts tras re-login). Añadido `clearDraft` al import |
| `src/views/PrivacyPolicyView.vue:13` | **BUG REAL #2** — el template usa `t('privacy.title')` etc. pero el setup solo desestructuraba `{ locale }` de `useI18n()` → `t is not defined` al renderizar la página pública `/privacy` (i18n `legacy:false` sin `globalInjection`, así que no hay `t` global). Ahora `const { locale, t } = useI18n()` |
| `src/components/labels/LabelPrintModal.vue` | **BUG REAL #3** — el template llama `@click="openDesigner"` (2 botones) pero la función no existía (solo la ref `designerOpen`); sin `openDesigner` el click lanzaba error y no abría el diseñador de plantillas. Añadida `function openDesigner()` que setea `designerOpen=true` |

Verificado: `node scripts/check-sfc-refs.mjs` → `✓ 39 SFC analizados, 0 referencias no resueltas` (exit 0); `npm run lint` OK; `npm run test` 15/15 OK; `npm run build` OK. Prueba de línea con probe temporal: template línea 3/4 y setup línea 12 reportados con precisión. Falsos positivos eliminados: literales regex (`/^\d+-Q/`, `/[T:]/g`, `[A-Za-z0-9]`...) y `BarcodeDetector` (API Shape Detection) registrada en GLOBALS. Evolución del conteo de issues: template 72→60→5→0; setup 2010→123→87→23→0.

## Recent session changes (Aug 25, 2026 (3) — start-all.sh robusto e idempotente)
**Verificado E2E**: arranque completo desde frío (10 servicios + frontend, todos healthy), re-ejecución con stack vivo → los 10 servicios y el frontend se omiten ("YA está healthy"), Ctrl+C mata hijos + huérfanos (patrón pkill corregido). Stack dejado levantado al terminar la sesión.

| File | Change |
|------|--------|
| `start-all.sh` | **Prerrequisitos upfront** (java/node/curl con hint de instalación, falla temprano); flag nuevo `--observability|-o` (Prometheus+Grafana vía compose, no bloqueante); **idempotencia**: `start_service` salta si el puerto ya responde healthy; `free_port_if_unhealthy` libera puertos ocupados por instancias colgadas (vía ss→pid); frontend: `npm install` automático si falta node_modules, skip si :5173 ya sirve; **verificación final smoke** (gateway/auth/frontend) con resumen ❌/✅; FIX del pkill del cleanup (`java -jar.*aircargo.*\.jar` nunca calzaba porque JAVA_OPTS se interpone → `[j]ava .*aircargo-(gateway|.*-service)-1\.2\.0-SNAPSHOT\.jar`, verificado contra los 10 procesos vivos con pgrep). Lección: NO matar procesos Java globalmente al arrancar — anulaba la idempotencia matando stacks de otros launchers vivos |

## Recent session changes (Aug 25, 2026 (2) — Observabilidad + resiliencia RabbitMQ + README + tests)
Cierra 4 debilidades de la tabla de auditoría. **Verificado E2E**: `/actuator/prometheus` sirve métricas con etiqueta `application="aircargo-auth"`; reactor compila con micrometer-registry-prometheus (runtime scope en common → heredado por los 10 módulos); tests common 19 + auth 30 BUILD SUCCESS.

| File | Change |
|------|--------|
| `backend/aircargo-common/pom.xml` | `micrometer-registry-prometheus` (runtime, versión gestionada por Boot BOM 1.13.0) — todos los servicios heredan el registro Prometheus |
| 9 × `application.properties` | `management.endpoints.web.exposure.include=health,info,prometheus` + `management.metrics.tags.application=aircargo-<svc>` |
| gateway `application.properties` | Ídem, conservando sus endpoints propios (gateway/circuitbreakers/ratelimiters) |
| 7 × `SecurityConfig` (auth/export/load-planning/notification/uld/warehouse/gateway-reactivo) | `/actuator/**` permitAll para que Prometheus scrapee sin JWT (booking ya lo tenía; flight/mawb ya eran permitAll total). JwtGatewayFilter ya tenía `/actuator/` público |
| `docker/docker-compose.observability.yml` | **NEW** — single-host: Prometheus :9090 (scrapea host.docker.internal:8080+9092-9100 vía extra_hosts host-gateway, retención 15d) + Grafana :3001 con datasource pre-provisionado |
| `docker/prometheus.yml` + `docker/grafana/provisioning/datasources/prometheus.yml` | **NEW** — config de scrape (servicios + rabbitmq:15692) y datasource |
| `docker/docker-compose.infrastructure.yml` + `docker/rabbitmq/enabled_plugins` | RabbitMQ ahora habilita plugin **rabbitmq_prometheus** (puerto 15692 expuesto) |
| `notification-service config/RabbitConfig.java` | Resiliencia: cola durable con **DLX** (`aircargo.dlx` → `aircargo.notifications.dlq`) + factory `retryListenerFactory` (3 intentos, backoff 1s×2, `RejectAndDontRequeueRecoverer`). Listener usa `containerFactory="retryListenerFactory"`. ⚠️ Si existe la cola vieja sin DLX: borrar una vez (`rabbitmqctl delete_queue aircargo.notifications`) o PRECONDITION_FAILED al arrancar |
| 5 × `application.properties` (flight/booking/mawb/warehouse/uld + notification dedup) | Publisher confirms `correlated`, `publisher-returns=true`, `template.mandatory=true` — publicaciones no enrutables quedan visibles en logs |
| `README.md` | **Reescrito completo** — tabla de 10 servicios con puertos, estructura real (ya sin aircargo-api), inicio rápido con .env/start-all.sh, observabilidad, backups/rollback, Power BI vía gateway, despliegue k8s. Corregido "TypeScript"→JS en stack |
| `common .../security/ObservabilityConsistencyTest.java` | **NEW** (3 tests) — guard estilo SecurityConfigConsistencyTest: O1 prometheus expuesto en los 10 servicios, O2 etiqueta application presente, R1 publishers AMQP con confirms/returns/mandatory, R2 notification con DLQ+retry+recoverer+listener wire |
| `auth .../service/BackupConfigServiceTest.java` | **NEW** (7 tests) — resolución ''→default, defaults sin fila, ruta relativa/vacía rechazada, creación automática de carpeta, merge null-safe, compresión fuera de rango |

Tests: common **19** (16 previos + 3 observabilidad), auth **30** (23 previos + 7 backups). Nota: micrometer-registry-prometheus 1.13.0 descargada a ~/.m2 — builds offline `-o` siguen funcionando.

## Recent session changes (Aug 25, 2026 — Protocolo de backup automático + rollback con auto-restore)
Sistema completo de respaldo/restauración gestionable desde Settings → Backups (ADMIN/SUPER_USER). **Verificado E2E**: PUT config persiste carpeta en BD → trigger MANUAL genera dump + registra historial → restore `--clean --if-exists` revierte cambios (keep_days 7→45) → flag `/tmp/aircargo-rollback-flag` detectado y consumido por el bloque de arranque. Tests: auth 23 OK (incluye SecurityConfigConsistencyTest), lint/build frontend OK.

| File | Change |
|------|--------|
| `scripts/db-backup.sh` | Reescrito — lee config desde `backup_config` (BD) sin jq (columnas individuales psql); soporta POSTGRES_HOST; tipos daily/pre-deploy/post-deploy/manual; registra en `backup_history` (best-effort, nunca falla si la BD está caída); `-Z` configurable |
| `scripts/rollback.sh` | **NEW** — protocolo: `--pre-deploy` (punto de rollback antes de deploy), `--post-deploy`, `--emergency` (backup de protección + flag para auto-restore en el próximo arranque), `--list`, `--restore FILE` (inmediato, `--clean --if-exists --no-owner`); log en `$BACKUP_DIR/rollback.log` |
| `start-all.sh` | Bloque AUTO-RESTORE entre infra y build — si existe el flag, prefiere el último `*_pre-deploy_*.dump` (fallback al más reciente), restaura vía pg_restore y elimina el flag |
| `~/.config/systemd/user/aircargo-backup.{service,timer}` | service ahora pasa tipo `daily`; timer diario 02:00 Persistent=true (activo, próxima corrida verificada) |
| `backend/.../authservice/entity/BackupConfig.java` | **NEW** — singleton id=1 (@Version): backupDir (''=default $HOME/aircargo-backups), keepDays, compressLevel, autoBackupEnabled/Schedule, notificaciones |
| `backend/.../authservice/entity/BackupHistory.java` | **NEW** — append-only por backup (fileName/path/sizeBytes/type/status/durationMs); índices created/type/status; @Builder.Default en createdAt |
| `backend/.../authservice/repository/{BackupConfigRepository,BackupHistoryRepository}.java` | **NEW** |
| `backend/.../authservice/dto/BackupDTOs.java` | **NEW** — BackupConfigDTO, BackupHistoryDTO, BackupStatsDTO |
| `backend/.../authservice/service/BackupConfigService.java` | **NEW** — getConfig resuelve ''→default del sistema; updateConfig valida ruta absoluta, CREA la carpeta si no existe, exige permisos de escritura, merge null-safe (campos no enviados no se pisan), compresión 0-9, keepDays≥1; getStats/getHistory/getLatest/getLatestPreDeploy |
| `backend/.../authservice/controller/BackupConfigController.java` | **NEW** — GET/PUT `/api/backup/config`, GET `/stats`, `/history?page&size`, `/latest`, `/latest-pre-deploy`, POST `/trigger?type=` (ejecuta scripts/db-backup.sh sincrónicamente con resolución de ruta subiendo directorios desde cwd); @PreAuthorize ADMIN/SUPER_USER |
| `backend/.../authservice/config/SecurityConfig.java` | `/api/backup/**` → ADMIN/SUPER_USER |
| `gateway RouteConfig.java` | `/api/backup/**` añadido a la ruta auth-service |
| `backend/.../db/migration/V22__create_backup_tables.sql` ≡ raíz `V49` | Tablas `backup_config` (CHECK id=1) + `backup_history`; seed idempotente con `WHERE NOT EXISTS` (**nunca sobrescribe** la carpeta ya configurada por el admin); grants a aircargo_user |
| `frontend/src/api/backups.js` | **NEW** — getConfig/updateConfig/getStats/getHistory/trigger |
| `frontend/src/views/SettingsView.vue` | **NEW tab Backups** (ADMIN/SUPER_USER): stats (total/tamaño/%éxito/disco libre), formulario de carpeta editable con guardado (crea la carpeta si no existe), retención/compresión, botón "Crear backup ahora" (auto-refresh 3s), tabla historial (archivo/tipo/tamaño/estado/fecha, refresh manual), nota de comandos rollback |
| `frontend/src/i18n/es.js` + `en.js` | Claves `settings.tabs.backups` + `settings.backups.*` |

Lección shell re-confirmada: `pkill -f "aircargo-auth-service"` se auto-mata porque el propio comando contiene el literal — usar `pkill -f "[a]uth-service-..."` (bracket evita el self-match).

## Recent session changes (Aug 22, 2026 (3) — secret.yml fuera del repo)
`k8s/secret.yml` (manifest K8s con placeholders `${POSTGRES_USER}/${POSTGRES_PASSWORD}/${JWT_SECRET}`, sin valores reales) movido a `~/Desktop/Projects/Rannik/aircargo-deploy-secrets/secret.yml` — carpeta hermana FUERA del proyecto. `.gitignore` ahora bloquea `k8s/secret*.yml` para evitar re-creación accidental. Los demás manifests de `k8s/` referencian el Secret por nombre (`aircargo-secrets`) así que no requieren cambios; al desplegar hay que aplicar la carpeta externa además de `k8s/`. Motivación: Graphify lo marcó como archivo potencialmente sensible durante el indexado del grafo.

## Recent session changes (Aug 23, 2026 (22) — Fix drag & drop de ULD a franja flotante)
**Bug**: arrastrar un ULD asignado del load plan hacia la franja de ULDs flotantes no hacía nada — el ULD seguía en el manifiesto.
Causas (ambas frontend, el backend ya soportaba null): (1) la sección `floating-drop-zone` no tenía `@dragover/@drop`; (2) `reassignFlight` hacía `return` silencioso si `flightId` era null.
- **Fix**: handlers de drop en la franja (`onDropFloating` con feedback visual ring+bg), guard relajado para aceptar `flightId: null`, lectura del id arrastrado desde ambos orígenes (`draggedUldId` de tarjetas y `rowDragging` de filas).
- **Caso borde cubierto**: si el ULD desasignado queda en estado OPEN no aparece en la franja flotante (filtro existente excluye OPEN) → se hace PATCH a IN_RAMP para mantenerlo visible.
- E2E: PATCH flightId=null → flight_id NULL en BD ✓ y restauración del vuelo OK. Lint/build OK.

## Recent session changes (Aug 24, 2026 (24) — Logout automático por inactividad 10 min)
- **`composables/useIdleLogout.js`**: eventos de actividad (pointer/key/wheel/touch/scroll) reinician el timer; aviso a los **8 min** (`components/IdleWarningModal.vue` con cuenta regresiva + "Seguir trabajando" que re-anuncia heartbeat); a los **10 min** → snapshot de formularios → `auth.logout()` (revoca cookies/sesión) → `/login?idle=1`.
- **Preservación de datos**: `utils/formDraft.js` captura TODOS los input/textarea/select visibles con firma estructural a sessionStorage; tras re-login, LoginView ofrece restauración (re-dispatch de input/change actualiza los v-model) y retorna a la vista original. `sessionStorage`: draft + return_to.
- Integrado en App.vue (`watch isAuthenticated` start/stop + modal). i18n `idle.*` es/en. Backend: access token TTL 1h→**15 min** defense-in-depth (el límite UX lo fija el cliente en 10).
- Nota: snapshot estructural — campos con firma distinta al restaurar se omiten (seguro).

## Recent session changes (Aug 24, 2026 (23) — Fix 500 en descargas de recibos)
**Causa**: el cifrado AES de la debilidad #2 expande las cédulas (~1.8x) y ya no cabían en `delivered/received/broker_id_num VARCHAR(50)` → cualquier guardado del recibo (incluida la regeneración de artefactos al exportar) fallaba con "value too long".
- **`V3__widen_id_num_columns_for_encryption.sql`** (warehouse, ≡ raíz V48): las 3 columnas → **VARCHAR(200)**; entidad actualizada a length=200 para `ddl-auto=validate`.
- Verificado E2E: export del recibo que fallaba → HTTP 200 Excel 53 KB. Flyway V3 registrada (history success=true). Barrido de ERROR-level en los 10 logs: sin errores nuevos tras los boots actuales.
- Lección ops: al cifrar columnas con @Convert, auditar longitudes — Base64+prefijo crece ~1.8x; TEXT para firmas ya era suficiente.

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

## Recent session changes (Aug 31, 2026 — MFA obligatorio con re-enrolamiento forzado en cada reinicio)

**Requisito de negocio**: en cada arranque del auth-service (local, EC2, k8s, etc.) se debe forzar la re-configuración de MFA para que ningún usuario quede enrolado con una configuración distinta a la esperada.

| File | Change |
|------|--------|
| `backend/.../authservice/db/migration/V24__add_mfa_policy.sql` ≡ raíz `V57__add_mfa_policy.sql` | **NEW** — tabla singleton `mfa_policy(id=1, last_reset_at, reset_on_startup, max_age_days)` + columna `app_user.mfa_enrolled_at`; seed idempotente con `reset_on_startup=true`, `max_age_days=7` |
| `backend/.../entity/MfaPolicy.java` | **NEW** — entidad JPA para la tabla singleton |
| `backend/.../repository/MfaPolicyRepository.java` | **NEW** — repo para la tabla |
| `backend/.../service/MfaPolicyService.java` | **NEW** — evalúa elegibilidad MFA: `OK` / `REQUIRED` (nunca enrolado o legacy sin timestamp) / `RESET_REQUIRED` (enrolado antes del último reinicio) / `EXPIRED` (> max_age_days); `policy()` crea singleton lazy con sentinel `lastResetAt = now-3650d` (evita invalidar enrolamientos actuales al arrancar en BD vacía); `resetNow()` adelanta epoch global; `requiresReenrollment()` usado en gates |
| `backend/.../config/MfaStartupResetRunner.java` | **NEW** — `ApplicationRunner` (`@Profile("!test")`): en cada arranque llama `resetNow()`, revoca sesiones de usuarios MFA (`tokens_valid_from`), audita `MFA_POLICY_RESET`; no aborta arranque si falla |
| `backend/.../entity/AppUser.java` | Añadido `mfaEnrolledAt` (TIMESTAMPTZ) |
| `backend/.../repository/AppUserRepository.java` | Añadido `revokeSessionsForMfaUsers(OffsetDateTime)` — actualiza `tokens_valid_from` |
| `backend/.../command/LoginCommandHandler.java` | Gate MFA: si `requiresReenrollment(user)` → `LoginOutcome.MFA_ENROLLMENT_REQUIRED` con `mfaReason` (required/reset/expired) |
| `backend/.../command/SetPasswordCommandHandler.java` | Igual gate MFA |
| `backend/.../controller/AuthController.java` | `setPasswordByToken` con gate MFA; inyecta `MfaPolicyService`; endpoints enroll `setup/enable` relajados (permiten re-enrolar si la política lo exige) |
| `backend/.../service/MfaService.java` | `enableMfa()` setea `mfaEnrolledAt=now()`; `disableMfa()` limpia |
| `backend/.../common/auth/JwtUtil.java` | `generateEnrollToken()` ahora incluye `jti=UUID` (fix: JJWT serializa iat/exp en segundos → dos tokens en el mismo segundo eran byte-idénticos y el único-uso revocaba el token fresco) |
| `backend/.../controller/MfaMandatoryIntegrationTest.java` | Ampliado a 6 tests: flujo completo enroll→setup→enable→re-login TOTP; 428 con reason `reset` tras reinicio; 428 con reason `expired` tras antigüedad; 428 en set-password sin MFA |
| `frontend/src/views/LoginView.vue` | Paso `mfa-enroll` muestra banner ámbar con icono `ShieldAlert` y mensaje según `mfaReason` (`reset`/`expired`/`required`); i18n es/en |
| `frontend/src/composables/useIcons.js` | Añadido `IconShieldAlert` (lucide) |
| `frontend/src/i18n/es.js` + `en.js` | Claves `login.mfaEnroll.*` actualizadas |

**Verificación E2E real vía gateway**:
1. Arranque auth-service → runner adelanta epoch (`last_reset_at=now`), migración V24 aplicada
2. Usuario `jsantos@rannik.com` (MFA previo legacy, `mfa_enrolled_at=NULL`) → login → **428** `mfaReason=required` (legacy) → setup/enable → MFA nuevo con timestamp
3. Login normal → pide TOTP (no re-enrolar)
4. Simular reinicio (adelantar epoch en BD) → login → **428** `mfaReason=reset` con mensaje "Por seguridad, la autenticación de dos factores fue reiniciada tras una actualización del sistema..."
5. Re-enrolamiento completo → login con TOTP OK
6. Tests: auth-service **36/36** pass; reactor completo **102/102** pass; frontend lint/build/vitest OK

## Recent session changes (Sep 1, 2026 — Dashboard Builder: campos seleccionables de todas las tablas + filtros)

**Requisito de negocio**: el Dashboard Builder debía permitir elegir **qué campos de qué tablas** aparecen en las consultas personalizadas, filtrar las filas y agrupar. Antes: catálogo hardcodeado de 12 campos (ULD/Vuelo), `fieldSources` se guardaba pero NO controlaba las columnas del resultado, filas por ULD (no por ULD-AWB), sin filtros, y el frontend usaba claves i18n `db.*` que NO existían (se renderizaban crudas).

**Decisiones de diseño**:
- Cada fila del resultado = **una MAWB dentro de un ULD** (detalle `uld_awb`), con joins a ULD→Flight→Airline→MAWB→Booking (por mawbId, fallback por awbNumber)→WarehouseReceipt (prefiere recibo no superseded).
- Los **filtros (WHERE)** viajan dentro de `cfg.chartConfig.filters` (`List<{field,op,value}>`) — persistidos con el reporte SIN migración de BD. Ops: `eq, ne, contains, gt, gte, lt, lte, isNull, notNull`.
- `fieldSources` ahora **controla las columnas** del reporte (dimension + selección + fórmulas). `MAX_ROWS=5000`.
- **Agrupación ULD-aware**: al agrupar, los pesos del ULD suman por ULD **distinto** no por fila (`ULD_LEVEL_KEYS = {TareLbs, GrossLbs, NetLbs, TareKg, GrossKg, NetKg}`), `FLIGHT_LEVEL_KEYS = {MaxPayloadKg}` por vuelo; el resto de numéricos suma plano; no-numérico toma el primer valor no-null. `LinkedHashMap` para orden estable.
- Aliases legacy preservados para reportes/fórmulas viejas: `Pieces`→piezas ULD-AWB, `Origin`/`Destination`→del vuelo, `Status`→estado del ULD.

| File | Change |
|------|--------|
| `backend/.../exportservice/service/DashboardBuilderService.java` | **REESCRITO** — catálogo de **61 campos** en 7 grupos (ULD 12, Flight 8, Airline 2, MAWB 12, Booking 13, Receipt 13, Scenario 1), cada `FieldDefDTO` con `source`; motor de filas ULD-AWB con joins; `filtersOf(cfg)` lee `chartConfig["filters"]`; `matchesFilters`; `groupBy` + `aggNumeric` (distinct-aware); `varsFor`/`totalsFor`; recorte de columnas a `dimension + fieldSources + fórmulas` |
| `backend/.../exportservice/dto/FilterDTO.java` | **NEW** — record `filter(String field, String op, Object value)` (accessors `flt.field()`/`flt.op()`/`flt.value()`) |
| `backend/.../exportservice/pom.xml` | Añadido `spring-boot-starter-test` (scope test) — el módulo no tenía infraestructura de tests |
| `backend/.../exportservice/service/DashboardBuilderServiceTest.java` | **NEW** — 5 tests con repos mockeados: catálogo ≥40 con todas las fuentes, 1 fila por ULD-AWB con joins (UldNumber/FlightNumber/AirlineCode/MawbStatus), filtros eq/gte/isNull, agrupación con **GrossLbs sumado por ULD distinto + MaxPayloadKg por vuelo**, columnas = dimension+fieldSources |
| `frontend/src/components/DashboardBuilderPanel.vue` | **REESCRITO** — selector de campos **agrupado por tabla** (checkbox por color, columna fuente en tooltip, todos los tipos seleccionables); sección **Filtros de consulta** (campo+operador+valor; input numérico/texto, dropdown Sí/No para booleanos, vacío/no-vacío sin valor), múltiples filtros AND; filtros se envían dentro de `chartConfig` al evaluar/guardar y se restauran al cargar; celdas booleanas renderizadas Sí/No; dimensión incluye campos string+boolean; grupos: uld/flight/**airline**/mawb/booking/receipt/scenario |
| `frontend/src/i18n/es.js` + `en.js` | **NEW bloque `db`** — claves del builder completas (antes inexistentes): fuentes (uld/flight/airline/mawb/booking/receipt/scenario), operadores, filtros, hint de columnas. Verificado: 0 claves faltantes en ambos idiomas |

**Verificación**:
- Reactor Maven completo BUILD SUCCESS (auth 36, flight 11, booking 7, mawb 11, warehouse 4, uld 11, export **5 nuevos**, common/gateway): **107 tests**.
- Frontend: check:refs 43 SFC 0 refs, lint OK, build OK, vitest 15/15.
- **E2E real vía gateway** (token acuñado con el `JWT_SECRET` real del `.env` — clave UTF-8 cruda, NO Base64): `fields` → 61 campos; sin token → 401; `evaluate` sin agrupar → joins reales ULD↔Flight↔Airline↔MAWB; agrupación `AwbNumber` → **GrossLbs 2500** (2 ULDs distintos) y Pieces 7 (4+3); filtros AND (`Pieces≥5` + `contains "585"`) → 1 fila; fórmula `Pieces*tasaCrecimiento`(1.1) → 5.5; CRUD reportes con filtros persistidos (create/get/update/delete 204). Datos temporales de prueba insertados/eliminados (uld_awb vuelve a 0).
- Nota: el export-service en marcha quedó RECONSTRUIDO y reiniciado con el jar nuevo (el proceso anterior corría el catálogo viejo).

## Import paths

Frontend uses `@/` → `./src/` (configured in `vite.config.js`).

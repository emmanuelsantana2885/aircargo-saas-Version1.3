# AirCargo SaaS

Sistema integral de gestión de carga aérea (SaaS multi-servicio): control de vuelos, reservas, MAWBs/HAWBs, recepción en bodega, ULDs, planificación de carga, compliance aduanal, notificaciones y analítica BI.

## Arquitectura

Microservicios Spring Boot detrás de un API Gateway reactivo, con frontend Vue 3. Toda petición pasa por el gateway (:8080), que valida JWT, aplica rate limiting y enruta al servicio correspondiente.

| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| **gateway** | 8080 | JWT, rate limiting, circuit breakers, CORS, Swagger agregado |
| **auth-service** | 9092 | Login (CQRS), MFA, usuarios, sitios, auditoría, backups, roles/permisos |
| **flight-service** | 9093 | Vuelos + aerolíneas + tipos de aeronave |
| **booking-service** | 9094 | Reservas + asignación de AWB |
| **mawb-service** | 9095 | MAWB + HAWB + compliance DUA + plantillas de etiquetas |
| **warehouse-service** | 9096 | Recibos de bodega + exportes PDF/Excel + evidencias |
| **uld-service** | 9097 | ULD/ULD-AWB/piezas + escaneo de códigos + eventos SSE |
| **load-planning-service** | 9098 | Planificación de carga + import ramp manifest + manifiestos |
| **export-service** | 9099 | Analítica de solo lectura: Export, BI, Reportes, Catálogo |
| **notification-service** | 9100 | Notificaciones + listeners RabbitMQ + email |

**Stack**: Vue 3 + Vite + Pinia + Tailwind · Spring Boot 3.3 (Java 21) · PostgreSQL 16 (Flyway por servicio) · RabbitMQ 4 · JWT httpOnly cookies + BCrypt + MFA TOTP · AES-256-GCM en reposo.

## Estructura del Proyecto

```
aircargo-saas/
├── frontend/                    # Vue 3 + Vite (puerto 5173)
│   └── src/{api,stores,views,components}
├── backend/
│   ├── aircargo-common/         # Entidades compartidas, JWT, DTOs, cifrado, auditoría
│   ├── aircargo-feign-clients/  # Contratos Feign entre servicios
│   ├── aircargo-gateway/
│   └── aircargo-{auth,flight,booking,mawb,warehouse,uld,load-planning,export,notification}-service/
├── database/migrations/         # Copia consolidada de todas las migraciones Flyway
├── docker/                      # Compose: infraestructura, servicios, observabilidad
├── deploy/                      # K8s kustomize (staging/production) + guías
├── scripts/                     # db-backup.sh, rollback.sh, smoke-e2e.sh
└── start-all.sh                 # Arranque full-stack local con health checks
```

## Inicio Rápido

### Requisitos

JDK 21+ · Maven (o IntelliJ bundled) · Node.js 18+ · Docker (o PostgreSQL/RabbitMQ nativos) · `pg_dump` para backups.

### 1. Configurar secretos

```bash
cp .env.example .env
# Editar .env: JWT_SECRET, POSTGRES_PASSWORD, RABBITMQ_PASSWORD, APP_ENCRYPTION_KEY
# Genera secretos con: openssl rand -base64 64
```

### 2. Arrancar todo

```bash
./start-all.sh              # build + Postgres + RabbitMQ + 10 servicios + frontend
./start-all.sh --skip-build # arranque rápido reutilizando jars
tail -f ~/aircargo-logs/<servicio>.log   # logs por servicio (rotación 10MB/14d)
```

El frontend queda en http://localhost:5173 y el gateway en http://localhost:8080.

### Tests y lint

```bash
cd backend && mvn test      # unitarias + integración (H2, sin Flyway)
cd frontend && npm run lint && npm test && npm run build
```

## Funcionalidades

- **Vuelos y reservas** — CRUD con caché Caffeine y paginación
- **MAWB / HAWB** — timeline de estados, evidencias, compliance DUA
- **Recepción en bodega** — recibos con firmas digitales, fotos de ID, PDF/Excel con desglose por HAWB, sistema de correcciones versionado
- **ULDs** — construcción, escaneo de piezas por código de barras (con SSE en vivo), transferencias entre vuelos, etiquetas ZPL/PDF
- **Planificación de carga** — drag & drop de ULDs, importación de ramp manifests Excel
- **Analítica** — dashboard BI, exportes CSV/XLSX/JSON, API Power BI
- **Seguridad** — RBAC de 7 roles, revocación central de tokens, bloqueo por intentos, auditoría inmutable con retención de 24 meses y IP pseudonimizada
- **Backups** — respaldo diario automático, carpeta configurable desde la UI, rollback con auto-restore (`scripts/rollback.sh`)

## Observabilidad

Cada servicio expone `/actuator/health` y `/actuator/prometheus` (métricas Micrometer/Prometheus). Stack local listo con:

```bash
docker compose -f docker/docker-compose.observability.yml up -d
```

- **Prometheus**: http://localhost:9090 — scrapea los 10 servicios + RabbitMQ (:15692)
- **Grafana**: http://localhost:3001 — datasource Prometheus pre-provisionado

En producción (k8s), el stack completo se instala con `deploy/production-setup/step2-observability/install-observability.sh` (kube-prometheus-stack + Loki).

## Backups y Rollback

```bash
./scripts/db-backup.sh daily          # respaldo manual (el timer systemd lo hace a las 02:00)
./scripts/rollback.sh --pre-deploy    # punto de restauración antes de actualizar
./scripts/rollback.sh --emergency     # flag de auto-restore → start-all.sh restaura solo
./scripts/rollback.sh --list          # listar backups
```

La carpeta de destino se gestiona desde **Settings → Backups** (ADMIN/SUPER_USER) y persiste en BD, de modo que sobrevive a nuevas instalaciones.

## API para Power BI

Endpoints de solo lectura con API key en el export-service:

```bash
curl "http://localhost:8080/api/bi/dashboard?api_key=TOKEN"
curl "http://localhost:8080/api/bi/flights?api_key=TOKEN"     # vuelos con métricas ULD
curl "http://localhost:8080/api/bi/bookings?api_key=TOKEN"    # reservas con fulfilment
```

La URL y el token se copian desde **Settings → API / BI**.

## Despliegue

```sh
./deploy/deploy.sh staging            # push a develop (CI/CD GitHub Actions)
./deploy/deploy.sh production         # push de tag v*  (requiere confirmación)
```

Guía completa: `deploy/PRODUCTION_DEPLOYMENT.md`.

---

**Emmanuel Santana Solano** — emmanuelsantana2885@gmail.com — Todos los derechos reservados

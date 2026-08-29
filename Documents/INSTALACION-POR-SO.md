# Instalación por SO — AirCargo SaaS

Guía de puesta en marcha del stack completo **sin importar la distro Linux** (Fedora, Ubuntu, Arch)
ni **Windows 11**. Todo el proyecto es portable: la configuración vive en variables de entorno
(`.env`), los scripts detectan el gestor de paquetes y los fallbacks nativos de PostgreSQL/RabbitMQ
solo se usan cuando Docker no está disponible.

---

## 1. Requisitos (comunes a todos los SO)

| Herramienta | Versión | Por qué |
|-------------|---------|---------|
| **Java (JDK)** | 21+ | Todo el backend compila con `<java.version>21</java.version>` (Spring Boot 3.3). Corretto/Temurin/OpenJDK valen. |
| **Maven** | 3.9+ | Build del reactor. Si no está en PATH, `aircargo-env.sh` lo busca solo (→ ver "Maven" abajo). IntelliJ bundled también vale. |
| **Node.js + npm** | 20.19+ (o 22.12+) | Frontend Vue 3 + Vite. `package.json` exige `^20.19.0 || >=22.12.0`. |
| **Docker** | 24+ / Compose v2 | Infraestructura (Postgres, RabbitMQ, Redis opcional). **Recomendado**; sin él el script usa instalaciones nativas de Postgres/RabbitMQ. |
| **openssl** | cualquier | Generar `JWT_SECRET` y `APP_ENCRYPTION_KEY`. |
| **psql / pg_dump** | coincide con Postgres | Backups (`scripts/db-backup.sh`) y creación de BD en fallback nativo. |
| **curl** | cualquier | Health checks y smoke tests. |
| **git** | cualquier | Clonar el repo. |

> **Maven**: los scripts prueban, en orden: variable `MAVEN_BIN` → `mvn` en PATH →
> IntelliJ bundled (flatpak, Linux) → `~/.sdkman/candidates/maven/current` → `/usr/share/maven`
> → `/opt/maven` → Homebrew (`/opt/homebrew`/`~/homebrew`). En casi todos los casos basta con
> `sudo dnf/apt/pacman install maven` o tenerlo en PATH.

---

## 2. Pasos comunes (Linux: Fedora / Ubuntu / Arch)

```bash
# 1) Clonar
git clone git@github.com:emmanuelsantana2885/aircargo-saas-Version1.3.git
cd aircargo-saas-Version1.3

# 2) Secretos (el archivo .env es gitignored — NUNCA se commitea)
cp .env.example .env
#    Rellena:
#      JWT_SECRET          → openssl rand -base64 64
#      POSTGRES_PASSWORD   → abreviación segura
#      RABBITMQ_PASSWORD   → abreviación segura
#      APP_ENCRYPTION_KEY  → openssl rand -base64 32
#      (APP_ENCRYPTION_KEY: si NO la pones, el cifrado en reposo usa passthrough con WARN al arrancar)

# 3) Arrancar TODO (build backend + frontend + infra + 10 servicios)
./start-all.sh

#    Opciones útiles:
./start-all.sh --skip-build   # reutiliza los jars ya compilados (arranque rápido)
./start-all.sh --only-backend # solo backend (sin Vite)
./start-all.sh --only-frontend# solo frontend (asume backend en :8080)
./start-all.sh --no-infra     # no tocar Postgres/RabbitMQ (ya corriendo)
./start-all.sh --observability# además Prometheus :9090 + Grafana :3001 (Docker)

# 4) Abrir
#    Frontend → http://localhost:5173
#    Gateway  → http://localhost:8080
#    Logs     → ~/aircargo-logs/aircargo-<servicio>.log   (rotación 10MB/14d)
```

> **Idempotente**: si un servicio ya responde healthy en su puerto, se salta su arranque.
> El script NO termina (queda monitorizando hasta Ctrl+C).

### Primer arranque y seeding

- El **DataSeeder** del auth-service crea la aerolínea UPS, los 4 sitios (SDQ/STI/PUJ/MIA),
  usuarios de prueba y permisos al arrancar sobre BD vacía.
- Las migraciones **Flyway** viven en cada microservicio (`backend/aircargo-*-service/.../db/migration/`)
  y se aplican solas con `baseline-on-migrate=true`.
- Usuarios de prueba (contraseña: usar el flujo "establecer contraseña" o `Generate reset link`
  desde Settings): `admin@aircargo.com`, `supervisor@aircargo.com` (→ SUPER_USER),
  `operations@aircargo.com`, `traffic@aircargo.com`, `loadplanner@aircargo.com`,
  `warehouse@aircargo.com`, `readonly@aircargo.com`.

---

## 3. Instalación de dependencias por distro

### Fedora (dnf)

```bash
# JDK 21
sudo dnf install -y java-21-openjdk-devel

# Node 20 LTS via nvm (lo recomendado; el package de dnf suele ir atrasado)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
export NVM_DIR="$HOME/.nvm"; [ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
nvm install 20 && nvm use 20

# Maven, git, openssl, curl, utilidades
sudo dnf install -y maven git openssl curl iproute

# Docker (moby) + Compose v2
sudo dnf install -y moby-engine docker-compose
sudo systemctl enable --now docker
sudo usermod -aG docker $USER    # re-loguear o newgrp docker

# PostgreSQL nativo (solo si NO usarás Docker para la BD; pkg_hint del script también lo sugiere)
sudo dnf install -y postgresql-server postgresql
```

### Ubuntu / Debian (apt)

```bash
sudo apt update && sudo apt upgrade -y

# JDK 21 (Temurin OpenJDK)
sudo apt install -y openjdk-21-jdk

# Node 20 via nvm (el de apt es viejo; se exige 20.19+)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
export NVM_DIR="$HOME/.nvm"; [ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
nvm install 20 && nvm use 20

# Maven, git, openssl, curl, iproute2
sudo apt install -y maven git openssl curl iproute2

# Docker Engine + Compose plugin
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker $USER

# PostgreSQL (opcional, para el fallback nativo sin Docker)
sudo apt install -y postgresql postgresql-client
```

> ⚠️ **Ubuntu/Debian**: `initdb` y `pg_ctl` NO están en PATH (viven en
> `/usr/lib/postgresql/<ver>/bin/`). `start-all.sh` ya los detecta solo por esa ruta.

### Arch Linux (pacman)

```bash
sudo pacman -Syu
sudo pacman -S jdk21-openjdk maven git openssl curl iproute2

# Node 20 LTS
sudo pacman -S nodejs-lts-iron npm

# Docker + Compose
sudo pacman -S docker docker-compose
sudo systemctl enable --now docker
sudo usermod -aG docker $USER

# PostgreSQL (opcional): sudo pacman -S postgresql
```

> ⚠️ **Arch**: el RabbitMQ **nativo** puede fallar si el Erlang del sistema no es compatible
> (boot con `unknown_instruction`; ver anotaciones del proyecto). Usa Docker para RabbitMQ:
> `./start-all.sh` levanta `aircargo-rabbitmq` automáticamente. El fallback nativo de
> Postgres (`initdb` local en `.local-pg/`, socket en `/tmp`) funciona bien en Arch.

---

## 4. Windows 11

El proyecto está pensado para bash (los scripts usan `/dev/tcp`, `ss`, `pkill`, globs estilo
`/usr/lib/postgresql/*/bin`). **No funciona directamente en PowerShell ni CMD.**

### Opción A — WSL2 + Ubuntu (RECOMENDADO, 100% soportado)

Igual que correr en Ubuntu nativo:

```powershell
# En PowerShell como admin
wsl --install
wsl --set-default-version 2
# Reiniciar, luego abrir el terminal Ubuntu

# Dentro de WSL (Ubuntu):
sudo apt update && sudo apt install -y git curl unzip
# instalar JDK 21, Node (nvm), Maven, Docker... igual que la sección Ubuntu de arriba
git clone git@github.com:emmanuelsantana2885/aircargo-saas-Version1.3.git
cd aircargo-saas-Version1.3
cp .env.example .env   # rellenar secretos
./start-all.sh
```

- Docker dentro de WSL2: instala **Docker Desktop for Windows** con WSL2 backend habilitado, o
  Docker Engine dentro de la distro. El daemon responde en el socket Unix normal.
- Guarda el repo DENTRO del filesystem WSL (`~/`) y NO en `/mnt/c/...` para evitar el overhead
  de filesystem 9P (el build de Maven sería mucho más lento si el repo está en la unidad C:).
- El puerto 5173/8080 se exponen vía `localhost` desde Windows automáticamente.

### Opción B — Docker Desktop (solo Docker)

Con Docker Desktop instalado y WSL2 backend, se puede levantar SOLO los contenedores
(orquestación completa con los Dockerfiles Temurin 21):

```powershell
cd docker
docker compose -f docker-compose.infrastructure.yml -f docker-compose.services.yml up -d
```

Esto da la infra + los 10 servicios + gateway, **pero no el frontend Vite** (el dev del
frontend requiere Node/npm). Para el frontend:

```powershell
cd frontend
npm ci
npm run build
# o npm run dev  →  vite `proxy` apunta a http://localhost:8080 (gateway)
```

> Limitaciones de Git Bash/MSYS2 (Opciones C y D NO soportadas):
> - `/dev/tcp` (usado por `port_up`) no existe → `start-all.sh` no puede comprobar puertos.
> - `ss` (iproute2), `pkill`, `pg_ctl`, `initdb` no están en Git Bash.
> - Rutas `/usr/lib/postgresql/*/bin` no existen en un Windows sin WSL.
>
> Por eso: **usar WSL2**, no Git Bash.

---

## 5. Backups / Rollback (portable)

```bash
./scripts/db-backup.sh manual           # dump custom comprimido → ~/aircargo-backups/
./scripts/rollback.sh --pre-deploy      # punto de rollback antes de un cambio
./scripts/rollback.sh --emergency       # backup de protección + flag de auto-restore
./scripts/rollback.sh --list            # listar dumps
./scripts/rollback.sh --restore FILE    # restauración inmediata
./scripts/smoke-e2e.sh                  # batería de 22 checks E2E (necesita ADMIN_EMAIL/ADMIN_PASS)
```

Requieren `psql`/`pg_dump`/`curl` en PATH (los de los capítulos anteriores) y que la BD
responda en `POSTGRES_HOST:POSTGRES_PORT`.

---

## 6. Solución de problemas rápidos

| Síntoma | Causa probable | Fix |
|---------|----------------|-----|
| `Required variable POSTGRES_PASSWORD is missing` | `.env` no copiado o vacío | `cp .env.example .env` y rellenar |
| Build offline falla con nuevas deps | `.m2` sin descarga previa | primer arranque con red: `mvn dependency:resolve` (o simplemente quitar `-o`) |
| Login devuelve 401 "Invalid or expired token" | Gateway sin el mismo `JWT_SECRET` que emite auth | el `.env` real debe tener el secret usado; `./start-all.sh` al inicio del gateway |
| RabbitMQ en PRECONDITION_FAILED | cola vieja sin DLX | `docker exec aircargo-rabbitmq rabbitmqctl delete_queue aircargo.notifications` y relanzar |
| 403 masivos tras expirar cookie | falta `authenticationEntryPoint(UNAUTHORIZED)` en un SecurityConfig nuevo | el guard `SecurityConfigConsistencyTest` en CI lo detecta |
| `ss: not found` | falta iproute2/iproute | `sudo apt/dnf/pacman install iproute2` / `iproute` |
| postgres `Permission denied` | instancia nativa del sistema ocupando :5432 | `sudo systemctl stop postgresql` o usar `--no-infra` si ya sirve tu propia BD |
</member>Notas de portabilidad
- `application.properties` de los 10 servicios leen de env vars con defaults
  (`POSTGRES_HOST:localhost`, `POSTGRES_PORT:5432`, `JWT_SECRET`, etc.) — sin rutas de OS.
- Logs: `$LOG_DIR` (default `~/aircargo-logs`), redirigible. `start-backend.sh` también lo usa.
- Datadir de Postgres nativo fallback: `.local-pg/` dentro del repo (gitignored).
- RabbitMQ nativo fallback: `~/.local-rabbitmq/`.
</member>
#!/usr/bin/env bash
# Desarrollado por Emmanuel Santana Solano
set -euo pipefail

# ────────────────────────────────────────────────────────────────
# Aircargo full-stack launcher (backend + frontend)
# ────────────────────────────────────────────────────────────────
#   --skip-build     | -b   → usa jars existentes (arranque rápido)
#   --only-backend   | -B   → solo backend (sin frontend Vite)
#   --only-frontend  | -F   → solo frontend (asume backend UP en :8080)
#   --no-infra       | -I   → no tocar Postgres/RabbitMQ (ya corriendo)
#   --observability  | -o   → además arranca Prometheus (:9090) y Grafana (:3001) vía Docker
#
# Idempotente: si un servicio YA está healthy en su puerto, se salta su arranque.
# Limpia procesos residuales de ejecuciones anteriores antes de arrancar.
# ────────────────────────────────────────────────────────────────

SKIP_BUILD=false
ONLY_BACKEND=false
ONLY_FRONTEND=false
NO_INFRA=false
OBSERVABILITY=false

for arg in "$@"; do
  case "$arg" in
    --skip-build|-b) SKIP_BUILD=true ;;
    --only-backend|-B) ONLY_BACKEND=true ;;
    --only-frontend|-F) ONLY_FRONTEND=true ;;
    --no-infra|-I) NO_INFRA=true ;;
    --observability|-o) OBSERVABILITY=true ;;
    --help|-h)
      echo "Uso: $0 [opciones]"
      echo ""
      echo "Opciones:"
      echo "  -b, --skip-build      Usa jars existentes (arranque rápido, sin recompilar)"
      echo "  -B, --only-backend    Solo backend (sin frontend Vite)"
      echo "  -F, --only-frontend   Solo frontend (asume backend UP en :8080)"
      echo "  -I, --no-infra        No tocar Postgres/RabbitMQ (ya corriendo)"
      echo "  -o, --observability   Arranca Prometheus (:9090) y Grafana (:3001)"
      echo "  -h, --help            Muestra esta ayuda"
      exit 0
      ;;
    *) echo "❌ Flag desconocido: $arg"; echo "Usa --help para ver las opciones"; exit 1 ;;
  esac
done

# ── Cargar secrets y validar ───────────────────────────────────
AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$AIR_ROOT/aircargo-env.sh"

PG_PORT="${POSTGRES_PORT:-5432}"
RMQ_PORT="${RABBITMQ_PORT:-5672}"
PG_DATA="$AIR_ROOT/.local-pg"
LOG_DIR="${LOG_DIR:-$HOME/aircargo-logs}"

# ═══════════════════════════════════════════════════════════════
#  PRERREQUISITOS — fallar temprano con mensaje claro
#  (mensajes de instalación adaptados al gestor de paquetes del SO)
# ═══════════════════════════════════════════════════════════════

# Detecta el comando de instalación del distro (hint para mensajes de error)
pkg_hint() {
  # $1 = package name (nombre lógico: jdk / node / docker / postgres / rabbitmq)
  local pkg="$1"
  case "${PKG_MGR:-}" in
    apt)   case "$pkg" in
            jdk) echo "sudo apt install -y openjdk-21-jdk" ;;
            node) echo "sudo apt install -y nodejs npm" ;;
            docker) echo "sudo apt install -y docker.io docker-compose-v2 && sudo systemctl enable --now docker" ;;
            postgres) echo "sudo apt install -y postgresql postgresql-client" ;;
            rabbitmq) echo "sudo apt install -y rabbitmq-server" ;;
            *) echo "sudo apt install -y $pkg" ;;
           esac ;;
    dnf)   case "$pkg" in
            jdk) echo "sudo dnf install -y java-21-openjdk-devel" ;;
            node) echo "sudo dnf install -y nodejs npm" ;;
            docker) echo "sudo dnf install -y moby-engine docker-compose && sudo systemctl enable --now docker" ;;
            postgres) echo "sudo dnf install -y postgresql-server postgresql" ;;
            rabbitmq) echo "sudo dnf install -y rabbitmq-server" ;;
            *) echo "sudo dnf install -y $pkg" ;;
           esac ;;
    pacman) case "$pkg" in
            jdk) echo "sudo pacman -S jdk21-openjdk" ;;
            node) echo "sudo pacman -S nodejs npm" ;;
            docker) echo "sudo pacman -S docker docker-compose && sudo systemctl enable --now docker" ;;
            postgres) echo "sudo pacman -S postgresql" ;;
            rabbitmq) echo "sudo pacman -S rabbitmq" ;;
            *) echo "sudo pacman -S $pkg" ;;
           esac ;;
    *)     case "$pkg" in
            jdk) echo "instala un JDK 21+ (Adoptium Temurin: https://adoptium.net)" ;;
            *) echo "instala $pkg con el gestor de paquetes de tu distro" ;;
           esac ;;
  esac
}

# Detecta distro → gestor de paquetes (best-effort, no bloqueante)
detect_pkg_mgr() {
  if command -v apt-get >/dev/null 2>&1; then echo "apt"
  elif command -v dnf >/dev/null 2>&1; then echo "dnf"
  elif command -v pacman >/dev/null 2>&1; then echo "pacman"
  else echo "unknown"; fi
}
PKG_MGR="${PKG_MGR:-$(detect_pkg_mgr)}"

echo "🔎 Verificando prerrequisitos (gestor de paquetes: ${PKG_MGR:-unknown})..."
PREREQ_FAIL=false

if ! command -v java >/dev/null 2>&1; then
  echo "  ❌ java no encontrado — $(pkg_hint jdk)"
  PREREQ_FAIL=true
else
  echo "  ✅ java ($(java -version 2>&1 | head -1 | cut -d'"' -f2))"
fi

if [ "$SKIP_BUILD" = "false" ] && [ "$ONLY_FRONTEND" = "false" ]; then
  echo "  ✅ maven ($MAVEN_BIN)"
fi

if [ "$ONLY_BACKEND" = "false" ]; then
  if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
    echo "  ❌ node/npm no encontrados — $(pkg_hint node)  (Node 20.19+ requerido)"
    PREREQ_FAIL=true
  else
    echo "  ✅ node $(node --version)"
  fi
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "  ❌ curl no encontrado — necesario para health checks"
  PREREQ_FAIL=true
fi

[ "$PREREQ_FAIL" = "true" ] && { echo "❌ Instala los prerrequisitos faltantes y reintenta."; exit 1; }

# ═══════════════════════════════════════════════════════════════
#  NOTA SOBRE PROCESOS RESIDUALES
#  No matamos procesos Java al arrancar: si un servicio ya está healthy
#  se omite (idempotencia); si su puerto está ocupado pero NO responde,
#  start_service libera el puerto antes de arrancar la instancia nueva.
# ═══════════════════════════════════════════════════════════════

free_port_if_unhealthy() {
  local port="$1"
  port_up "$port" || return 0
  # Ocupado Y healthy → lo maneja el skip de start_service
  curl -s "http://localhost:${port}/actuator/health" 2>/dev/null | grep -q '"status":"UP"' && return 0
  # Ocupado y NO healthy → liberar matando al proceso que escucha
  local pids
  pids=$(ss -tlnp 2>/dev/null | grep ":${port} " | grep -oE 'pid=[0-9]+' | sed 's/pid=//' | sort -u || true)
  if [ -n "$pids" ]; then
    echo "  ⚠️  Puerto :$port ocupado por proceso NO healthy (PID $pids) — liberando..."
    # shellcheck disable=SC2086
    kill -TERM $pids 2>/dev/null || true
    sleep 3
    # shellcheck disable=SC2086
    [ "$(ss -tln | grep -c ":${port} ")" != "0" ] && { kill -KILL $pids 2>/dev/null || true; sleep 1; }
  fi
}

# ── PIDs de procesos hijos para limpieza ───────────────────────
# =() evita "unbound variable" bajo `set -u` cuando todo arranca ya healthy
# (el array queda vacío porque ningún servicio fue iniciado por este script).
declare -A CHILD_PIDS=()
# Puerto de cada proceso → lo usa el monitor para distinguir "proceso
# sustituido por otro" de "servicio caído de verdad".
declare -A SERVICE_PORTS
CLEANUP_DONE=false

cleanup() {
  [ "$CLEANUP_DONE" = "true" ] && return
  CLEANUP_DONE=true
  echo ""
  echo "🛑 Señal recibida — deteniendo todos los servicios..."
  for name in "${!CHILD_PIDS[@]}"; do
    pid="${CHILD_PIDS[$name]}"
    if kill -0 "$pid" 2>/dev/null; then
      echo "  → Matando $name (PID $pid)"
      kill -TERM "$pid" 2>/dev/null || true
    fi
  done
  # Esperar un poco a que terminen graceful
  sleep 3
  for name in "${!CHILD_PIDS[@]}"; do
    pid="${CHILD_PIDS[$name]}"
    if kill -0 "$pid" 2>/dev/null; then
      echo "  → Force kill $name (PID $pid)"
      kill -KILL "$pid" 2>/dev/null || true
    fi
  done
  # Mata también procesos huérfanos de aircargo por si acaso
  # (patrón con [a] para no auto-matarse; JAVA_OPTS puede interponerse entre java y -jar)
  # El patrón es flexible con la versión del jar (matchea cualquier versión, no solo 1.2.0-SNAPSHOT)
  pkill -f "[j]ava .*aircargo-(gateway|.*-service)-[0-9].*\.jar" 2>/dev/null || true
  [ "$ONLY_BACKEND" = "false" ] && pkill -f "vite" 2>/dev/null || true
  echo "✅ Limpieza completa"
  exit 0
}

trap cleanup SIGINT SIGTERM EXIT

port_up() { (echo > "/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

docker_ok() { command -v docker >/dev/null 2>&1 && timeout 10 docker info >/dev/null 2>&1; }

# Detecta initdb/pg_ctl (establece PG_INITDB y PG_CTL globales).
# En Debian/Ubuntu los binarios PostgreSQL NO están en PATH: viven en
# /usr/lib/postgresql/<ver>/bin/ (Fedora/Arch sí usan /usr/bin, aunque a veces
# sin initdb/pg_ctl — ahí el datadir del sistema lo gestiona systemd y no se
# debe lanzar una instancia propia).
find_pg_bins() {
  PG_INITDB="" PG_CTL="" PG_BIN=""
  if command -v initdb >/dev/null 2>&1; then PG_INITDB="$(command -v initdb)"; fi
  if command -v pg_ctl >/dev/null 2>&1; then PG_CTL="$(command -v pg_ctl)"; fi
  if command -v postgres >/dev/null 2>&1; then PG_BIN="$(command -v postgres)"; fi
  for dir in /usr/lib/postgresql/*/bin /usr/bin /usr/local/bin; do
    [ -x "$dir/initdb" ]  && [ -z "$PG_INITDB" ] && PG_INITDB="$dir/initdb"
    [ -x "$dir/pg_ctl" ]  && [ -z "$PG_CTL" ] && PG_CTL="$dir/pg_ctl"
    [ -x "$dir/postgres" ] && [ -z "$PG_BIN" ] && PG_BIN="$dir/postgres"
  done
  # Si hay un Postgres del sistema activo (systemd), preferirlo y no tocar nada:
  # el fallback nativo propio solo debe usarse cuando no exista ningún Postgres.
  if command -v systemctl >/dev/null 2>&1 && systemctl is-active postgresql >/dev/null 2>&1; then
    PG_SYSTEMD=1
  else
    PG_SYSTEMD=0
  fi
  export PG_INITDB PG_CTL PG_BIN PG_SYSTEMD
}

wait_health() {
  local name="$1" port="$2" timeout="${3:-240}"
  echo "  ⏳ $name → http://localhost:${port}/actuator/health (timeout ${timeout}s)"
  local start_time=$(date +%s)
  local elapsed=0
  # -sf: en silencio ante fallos transitorios (curl rc 7 = connection refused) para
  # que bajo `set -o pipefail` el bucle no salga prematuramente por un error puntual.
  while ! curl -sf "http://localhost:${port}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
    elapsed=$(( $(date +%s) - start_time ))
    if [ "$elapsed" -gt "$timeout" ]; then
      echo "    ❌ $name no healthy after ${timeout}s"
      tail -30 "$LOG_DIR/$name.log" 2>/dev/null || true
      return 1
    fi
    sleep 3
  done
  elapsed=$(( $(date +%s) - start_time ))
  echo "    ✅ $name UP (${elapsed}s)"
}

start_service() {
  local name="$1" port="$2" timeout="${3:-240}" extra_env="${4:-}"
  local jar="$AIR_ROOT/backend/$name/target/${name}-1.2.0-SNAPSHOT.jar"
  [ -f "$jar" ] || { echo "❌ No se encontró $jar"; return 1; }

  # Idempotencia: si ya hay un servicio healthy en ese puerto, no lo tocamos
  if curl -s "http://localhost:${port}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
    echo "  ↪️  $name YA está healthy en :$port — omitido"
    return 0
  fi

  # Puerto ocupado por instancia muerta/colgada → liberarlo antes de arrancar
  free_port_if_unhealthy "$port"

  mkdir -p "$LOG_DIR"
  echo "  → Iniciando $name (puerto $port)"
  # exec: el proceso en segundo plano ES el propio java (no un wrapper bash),
  # así el cleanup con kill llega directo al PID real del JVM.
  local cmd="exec java ${JAVA_OPTS:-} -jar \"$jar\""
  # shellcheck disable=SC2086
  bash -c "$extra_env $cmd" >> "$LOG_DIR/$name.log" 2>&1 &
  local pid=$!
  CHILD_PIDS["$name"]=$pid
  SERVICE_PORTS["$name"]=$port
  echo "    [PID $pid] → $LOG_DIR/$name.log"

  wait_health "$name" "$port" "$timeout" || return 1
  return 0
}

# ═══════════════════════════════════════════════════════════════
#  MONITOR — keep-alive resiliente
#  Antes, el `wait` final bajo `set -e` abortaba TODO el stack en
#  cuanto UN solo servicio salía con código distinto de 0 (cualquier
#  crash/exit) — eso derribaba los 10 a la vez. Ahora el launcher se
#  queda vivo, avisa por log del caído y deja el resto intacto.
# ═══════════════════════════════════════════════════════════════

monitor() {
  echo ""
  echo "🧊 Monitor activo — el stack permanece levantado aunque un servicio caiga."
  echo "   Ctrl+C para detener todos los servicios."
  echo ""
  while true; do
    if [ "${#CHILD_PIDS[@]}" -gt 0 ]; then
      local names=("${!CHILD_PIDS[@]}")
      for name in "${names[@]}"; do
        local pid="${CHILD_PIDS[$name]}"
        if ! kill -0 "$pid" 2>/dev/null; then
          echo ""
          echo "  ⚠️  [$name] (PID $pid) terminó — el resto del stack sigue activo"
          local port="${SERVICE_PORTS[$name]:-}"
          local responds=false
          if [ -n "$port" ] && curl -s -o /dev/null --max-time 3 "http://localhost:${port}" 2>/dev/null; then
            responds=true
          fi
          if [ "$responds" = "true" ]; then
            echo "       el puerto :$port sigue respondiendo (proceso sustituido) — no se re-arranca"
          else
            echo "       últimos logs de $LOG_DIR/$name.log:"
            tail -25 "$LOG_DIR/$name.log" 2>/dev/null | sed 's/^/         /' || true
          fi
          unset CHILD_PIDS["$name"]
        fi
      done
    fi
    sleep 15
  done
}

# ═══════════════════════════════════════════════════════════════
#  OBSERVABILIDAD (opcional: --observability)
# ═══════════════════════════════════════════════════════════════

if [ "$OBSERVABILITY" = "true" ] && [ "$ONLY_FRONTEND" = "false" ]; then
  if docker_ok; then
    echo ""
    echo "📈 Arrancando stack de observabilidad (Prometheus :9090 + Grafana :3001)..."
    (cd "$AIR_ROOT/docker" && GRAFANA_ADMIN_PASSWORD="${GRAFANA_ADMIN_PASSWORD:-aircargo_admin}" \
      docker compose -f docker-compose.observability.yml up -d) || echo "⚠️  No se pudo levantar la observabilidad (no bloquea el arranque)"
  else
    echo "⚠️  --observability requiere Docker daemon activo — omitido (no bloquea)"
  fi
fi

# ═══════════════════════════════════════════════════════════════
#  INFRAESTRUCTURA (Postgres + RabbitMQ)
# ═══════════════════════════════════════════════════════════════

RABBITMQ_ENABLED=true

if [ "$NO_INFRA" = "false" ] && [ "$ONLY_FRONTEND" = "false" ]; then
  pg_up=false; rmq_up=false
  port_up "$PG_PORT" && pg_up=true
  port_up "$RMQ_PORT" && rmq_up=true

  # RabbitMQ en el puerto. Prioridad:
  #   1) Si hay un contenedor 'aircargo-rabbitmq' corriendo → usarlo (no tocar nada).
  #   2) Si hay un RabbitMQ nativo (systemd o proceso) SANO → usarlo TAL CUAL (no detenerlo):
  #      compartimos credenciales vía .env y no queremos tirar un broker que funciona
  #      para "ceder" el puerto a un contenedor que quizá no se levante.
  #   Solo se detiene un nativo si existe realmente un contenedor rabbitmq que lo
  #   reemplace de forma inmediata (caso de migración explícita).
  rmq_container=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -x 'aircargo-rabbitmq' || true)
  if [ "$rmq_up" = "true" ] && [ -n "$rmq_container" ] && docker_ok; then
    echo "🔄 RabbitMQ por contenedor Docker ('aircargo-rabbitmq') — deteniendo instancia nativa que ocupa :$RMQ_PORT..."
    # Solo detener systemd si existe un contenedor que ya está sirviendo (no dejar hueco).
    systemctl stop rabbitmq-server 2>/dev/null || true
    sleep 2
    port_up "$RMQ_PORT" || rmq_up=false
  fi

  # Si :5432 lo tiene nuestra instancia nativa vacía pero Docker está
  # disponible, detenerla y dejar que Docker sirva la BD real (volumen pgdata)
  find_pg_bins
  if [ "$pg_up" = "true" ] && [ -f "$PG_DATA/PG_VERSION" ] && [ -n "$PG_CTL" ] && "$PG_CTL" -D "$PG_DATA" status >/dev/null 2>&1 && docker_ok; then
    echo "🔄 Cediendo :$PG_PORT al Postgres de Docker (deteniendo instancia nativa vacía)..."
    "$PG_CTL" -D "$PG_DATA" -m fast stop || true
    sleep 2
    pg_up=false
  fi

  # Postgres MANDATORIO — Docker primero, fallback nativo
  if [ "$pg_up" = "false" ]; then
    if docker_ok; then
      echo "🐳 Docker disponible — arrancando contenedor Postgres..."
      docker compose -f "$AIR_ROOT/docker/docker-compose.infrastructure.yml" up -d postgres 2>/dev/null || \
      docker compose -f "$AIR_ROOT/docker/docker-compose.infrastructure.yml" up -d
    else
      # Docker no disponible. Si hay un Postgres del sistema activo (systemd),
      # usarlo directamente en lugar de lanzar una instancia .local-pg propia
      # (evita conflictos de puerto/datos con el datadir real del SO).
      if [ "$PG_SYSTEMD" = "1" ]; then
        echo "🐘 Docker no responde — usando PostgreSQL del SISTEMA (systemd), ya activo en :$PG_PORT..."
      else
        # Los binarios ya fueron detectados por find_pg_bins (ver arriba)
        if [ -z "$PG_INITDB" ] || [ -z "$PG_CTL" ]; then
          echo "❌ Ni Docker ni PostgreSQL nativo están disponibles." >&2
          echo "   Instala PostgreSQL o inicia el daemon de Docker." >&2
          exit 1
        fi
        echo "🐘 Docker no responde — usando PostgreSQL NATIVO (datadir: $PG_DATA)..."
        if [ ! -f "$PG_DATA/PG_VERSION" ]; then
          rm -rf "$PG_DATA"
          mkdir -p "$PG_DATA"
          "$PG_INITDB" -D "$PG_DATA" -U "${POSTGRES_USER:-aircargo_user}" -A trust >/dev/null
        fi
        "$PG_CTL" -D "$PG_DATA" -l "$PG_DATA/postgres.log" \
          -o "-p $PG_PORT -c max_connections=150 -c unix_socket_directories=/tmp" -w start
      fi
    fi
    echo "⏳ Esperando Postgres :$PG_PORT..."
    for _ in $(seq 1 30); do
      port_up "$PG_PORT" && { pg_up=true; break; }
      sleep 2
    done
    [ "$pg_up" = "true" ] || { echo "❌ Postgres no responde en :$PG_PORT"; tail -n 20 "$PG_DATA/postgres.log" 2>/dev/null; exit 1; }

    # Asegurar BD compartida (idempotente)
    PG_DB="${POSTGRES_DB:-aircargo}"
    if command -v psql >/dev/null 2>&1; then
      if ! psql -h 127.0.0.1 -p "$PG_PORT" -U "${POSTGRES_USER:-aircargo_user}" -d postgres -tAc \
           "SELECT 1 FROM pg_database WHERE datname='$PG_DB'" 2>/dev/null | grep -q 1; then
        echo "🗄️  Creando base de datos '$PG_DB'..."
        createdb -h 127.0.0.1 -p "$PG_PORT" -U "${POSTGRES_USER:-aircargo_user}" "$PG_DB"
      fi
    fi
    port_up "$RMQ_PORT" && rmq_up=true
  fi

  # RabbitMQ OPCIONAL — Docker, luego nativo
  if [ "$rmq_up" = "false" ]; then
    started_rmq=false
    if docker_ok; then
      echo "🐳 Docker disponible — arrancando contenedor RabbitMQ..."
      docker compose -f "$AIR_ROOT/docker/docker-compose.infrastructure.yml" up -d rabbitmq 2>/dev/null || true
      for _ in $(seq 1 20); do
        port_up "$RMQ_PORT" && { started_rmq=true; break; }
        sleep 2
      done
    fi
    if [ "$started_rmq" = "false" ] && command -v rabbitmq-server >/dev/null 2>&1; then
      echo "🐰 Usando RabbitMQ NATIVO..."
      export RABBITMQ_BASE="$HOME/.local-rabbitmq"
      export CONF_ENV_FILE="$RABBITMQ_BASE/rabbitmq-env.conf"
      mkdir -p "$RABBITMQ_BASE"
      rabbitmq-server -detached >/dev/null 2>&1 || true
      for _ in $(seq 1 20); do
        port_up "$RMQ_PORT" && { started_rmq=true; break; }
        sleep 2
      done
      if [ "$started_rmq" = "true" ] && command -v rabbitmqctl >/dev/null 2>&1; then
        RMQ_USER="${RABBITMQ_USER:-aircargo}"
        sleep 5
        rabbitmqctl add_user "$RMQ_USER" "$RABBITMQ_PASSWORD" >/dev/null 2>&1 || true
        rabbitmqctl change_password "$RMQ_USER" "$RABBITMQ_PASSWORD" >/dev/null 2>&1 || true
        rabbitmqctl set_permissions -p "/" "$RMQ_USER" ".*" ".*" ".*" >/dev/null 2>&1 || true
      fi
    fi
    rmq_up=$started_rmq
  fi

  if [ "$rmq_up" = "false" ]; then
    echo "⚠️  RabbitMQ(:$RMQ_PORT) no disponible — notification-service arranca SIN listeners AMQP"
    RABBITMQ_ENABLED=false
  else
    RABBITMQ_ENABLED=true
  fi

  echo "✅ Postgres :$PG_PORT OK $([ "$rmq_up" = "true" ] && echo "| RabbitMQ :$RMQ_PORT OK" || echo "| RabbitMQ: omitido")"
fi

# ═══════════════════════════════════════════════════════════════
#  AUTO-RESTORE POR ROLLBACK (protocolo: scripts/rollback.sh)
# ═══════════════════════════════════════════════════════════════

if [ "$NO_INFRA" = "false" ] && [ "$ONLY_FRONTEND" = "false" ]; then
  ROLLBACK_FLAG="/tmp/aircargo-rollback-flag"
  if [ -f "$ROLLBACK_FLAG" ]; then
    echo ""
    echo "🔁 FLAG DE ROLLBACK DETECTADO ($ROLLBACK_FLAG) — buscando backup para restaurar..."
    BACKUP_DIR_CFG="${BACKUP_DIR:-$HOME/aircargo-backups}"
    # Preferir el último pre-deploy; si no hay, el más reciente de cualquier tipo
    RESTORE_FILE="$(ls -t "$BACKUP_DIR_CFG"/*_pre-deploy_*.dump 2>/dev/null | head -1 || true)"
    [ -z "$RESTORE_FILE" ] && RESTORE_FILE="$(ls -t "$BACKUP_DIR_CFG"/*.dump 2>/dev/null | head -1 || true)"
    if [ -n "$RESTORE_FILE" ] && [ -f "$RESTORE_FILE" ]; then
      echo "   🎯 Restaurando: $RESTORE_FILE"
      export PGPASSWORD="$POSTGRES_PASSWORD"
      if pg_restore -h 127.0.0.1 -p "$PG_PORT" -U "${POSTGRES_USER:-aircargo_user}" \
           -d "${POSTGRES_DB:-aircargo}" --clean --if-exists --no-owner "$RESTORE_FILE"; then
        echo "   ✅ BD restaurada desde $(basename "$RESTORE_FILE")"
      else
        echo "   ❌ Falló pg_restore — revisa $HOME/aircargo-backups/ y restaura manualmente:"
        echo "      ./scripts/rollback.sh --restore \"$RESTORE_FILE\""
      fi
    else
      echo "   ⚠️  No hay backups en $BACKUP_DIR_CFG — no se restauró nada"
    fi
    rm -f "$ROLLBACK_FLAG"
    echo "   🧹 Flag de rollback eliminado"
  fi
fi

# ═══════════════════════════════════════════════════════════════
#  LOG ROTATION — limpiar logs viejos al arrancar
# ═══════════════════════════════════════════════════════════════

mkdir -p "$LOG_DIR"
# Eliminar logs de build de más de 7 días
find "$LOG_DIR" -name "build-*.log" -mtime +7 -delete 2>/dev/null || true
# Rotar logs de servicios si son mayores a 10MB
for logfile in "$LOG_DIR"/*.log; do
  [ -f "$logfile" ] || continue
  if [ "$(stat -c%s "$logfile" 2>/dev/null || echo 0)" -gt 10485760 ]; then
    mv "$logfile" "${logfile}.$(date +%Y%m%d).old" 2>/dev/null || true
  fi
done
# Eliminar .old de más de 14 días
find "$LOG_DIR" -name "*.log.*.old" -mtime +14 -delete 2>/dev/null || true

# ═══════════════════════════════════════════════════════════════
#  BUILD (si no --skip-build)
# ═══════════════════════════════════════════════════════════════

if [ "$SKIP_BUILD" = "true" ]; then
  echo "⚡ Flag --skip-build: usando jars existentes (sin recompilar)"
else
  if [ "$ONLY_FRONTEND" = "false" ]; then
    echo "🏗️ Construyendo backend (reactor completo, incluye aircargo-common y feign-clients)..."
    # Intentar offline primero (rápido si ~/.m2 está completo), fallback a online
    BUILD_LOG="$LOG_DIR/build-$(date +%Y%m%d-%H%M%S).log"
    mkdir -p "$LOG_DIR"
    if (cd "$AIR_ROOT/backend" && "$MAVEN_BIN" -o clean install -DskipTests -q) 2>>"$BUILD_LOG"; then
      echo "✅ Build offline OK"
    else
      echo "  ⚠️  Build offline falló — reintentando con dependencias online..."
      if (cd "$AIR_ROOT/backend" && "$MAVEN_BIN" clean install -DskipTests) >>"$BUILD_LOG" 2>&1; then
        echo "✅ Build online OK"
      else
        echo "❌ La compilación del backend ha fallado — log: $BUILD_LOG"
        tail -20 "$BUILD_LOG" 2>/dev/null || true
        exit 1
      fi
    fi
  fi
fi

# ═══════════════════════════════════════════════════════════════
#  BACKEND SERVICES (arranque escalonado con health checks)
# ═══════════════════════════════════════════════════════════════

if [ "$ONLY_FRONTEND" = "false" ]; then
  echo "🚀 Iniciando servicios backend (escalonado con health checks)..."

  # 1) Gateway — todo pasa por él
  start_service "aircargo-gateway" 8080 120 || exit 1

  # 2) Auth — necesario para JWT que usan los demás
  start_service "aircargo-auth-service" 9092 240 || exit 1

  # 3) Resto de servicios (orden de dependencias)
  declare -a services=(
    "aircargo-flight-service 9093"
    "aircargo-booking-service 9094"
    "aircargo-mawb-service 9095"
    "aircargo-warehouse-service 9096"
    "aircargo-uld-service 9097"
    "aircargo-load-planning-service 9098"
    "aircargo-export-service 9099"
    "aircargo-notification-service 9100"
  )

  for entry in "${services[@]}"; do
    name="${entry%% *}"
    port="${entry##* }"
    extra_env=""
    [ "$name" = "aircargo-notification-service" ] && extra_env="RABBITMQ_ENABLED=$RABBITMQ_ENABLED"
    start_service "$name" "$port" 240 "$extra_env" || exit 1
  done

  echo "✅ Todos los 10 servicios backend healthy (logs en $LOG_DIR/)"

  if [ "$ONLY_BACKEND" = "true" ]; then
    echo "🔧 Modo --only-backend: backend listo. Presiona Ctrl+C para detener."
    monitor
  fi
fi

# ═══════════════════════════════════════════════════════════════
#  FRONTEND (Vite dev server)
# ═══════════════════════════════════════════════════════════════

if [ "$ONLY_BACKEND" = "false" ]; then
  # Idempotencia: si algo ya escucha en :5173, asumimos que es nuestro Vite
  if curl -s -o /dev/null -w "%{http_code}" http://localhost:5173 2>/dev/null | grep -q "^2"; then
    echo "↪️  Frontend YA está corriendo en :5173 — omitido"
  else
    echo "🌐 Iniciando frontend (Vite dev server en puerto 5173)..."
    # Dependencias del frontend: instalar solo si faltan
    if [ ! -d "$AIR_ROOT/frontend/node_modules" ]; then
      echo "  📦 node_modules no existe — npm install (primera vez, puede tardar)..."
      (cd "$AIR_ROOT/frontend" && npm install --no-audit --no-fund) >> "$LOG_DIR/npm-install.log" 2>&1 || {
        echo "❌ npm install falló — revisa $LOG_DIR/npm-install.log"
        exit 1
      }
    fi
    mkdir -p "$LOG_DIR"
    (cd "$AIR_ROOT/frontend" && exec npm run dev) >> "$LOG_DIR/frontend.log" 2>&1 &
    FRONTEND_PID=$!
    CHILD_PIDS["frontend"]=$FRONTEND_PID
    SERVICE_PORTS["frontend"]="5173"
    echo "    [PID $FRONTEND_PID] → $LOG_DIR/frontend.log"

    # Esperar a que Vite esté listo
    FRONTEND_UP=false
    for _ in $(seq 1 30); do
      if curl -s -o /dev/null -w "%{http_code}" http://localhost:5173 2>/dev/null | grep -q "^2"; then
        echo "    ✅ Frontend UP"
        FRONTEND_UP=true
        break
      fi
      sleep 2
    done
    [ "$FRONTEND_UP" = "false" ] && {
      echo "    ❌ Frontend no respondió en 60s — últimos logs:"
      tail -20 "$LOG_DIR/frontend.log" 2>/dev/null || true
    }
  fi
fi

# ═══════════════════════════════════════════════════════════════
#  VERIFICACIÓN FINAL (smoke)
# ═══════════════════════════════════════════════════════════════

echo ""
echo "🩺 Verificación final..."
SMOKE_OK=true
for check in "Gateway|8080|/actuator/health" "Auth|9092|/actuator/health"; do
  cname="${check%%|*}"; rest="${check#*|}"; cport="${rest%%|*}"; cpath="${rest#*|}"
  if curl -s "http://localhost:${cport}${cpath}" 2>/dev/null | grep -q '"UP"'; then
    echo "  ✅ $cname :$cport"
  else
    echo "  ❌ $cname :$cport NO responde"
    SMOKE_OK=false
  fi
done
if [ "$ONLY_BACKEND" = "false" ] && ! port_up 5173; then
  echo "  ❌ Frontend :5173 NO responde"
  SMOKE_OK=false
fi
[ "$SMOKE_OK" = "false" ] && echo "  ⚠️  Hay componentes caídos — revisa $LOG_DIR/"

# ═══════════════════════════════════════════════════════════════
#  KEEP ALIVE — el script se queda en foreground hasta Ctrl+C
# ═══════════════════════════════════════════════════════════════

echo ""
echo "🎉 ✅ TODO EN MARCHA !"
echo "   📡 Backend (Gateway) : http://localhost:8080"
[ "$ONLY_BACKEND" = "false" ] && echo "   🌐 Frontend (Vite)    : http://localhost:5173"
[ "$OBSERVABILITY" = "true" ] && {
  echo "   📈 Prometheus         : http://localhost:9090"
  echo "   📊 Grafana            : http://localhost:3001 (admin / \${GRAFANA_ADMIN_PASSWORD:-aircargo_admin})"
}
echo ""
echo "🛠️  Utilidades:"
echo "   • Logs en tiempo real: tail -f $LOG_DIR/<servicio>.log"
echo "   • Detener todo: Ctrl+C (detiene solo los servicios arrancados por esta sesión;"
echo "     los que ya estaban healthy de otra sesión/launcher se dejan intactos)"
echo "   • Build logs: ls $LOG_DIR/build-*.log"
echo ""

# Mantener el script vivo esperando señales (resiliente ante crashes de
# servicios individuales — ver funciones monitor() y cleanup())
monitor
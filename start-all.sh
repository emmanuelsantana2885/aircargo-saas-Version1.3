#!/usr/bin/env bash
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
    *) echo "❌ Flag desconocido: $arg"; echo "Uso: $0 [--skip-build|-b] [--only-backend|-B] [--only-frontend|-F] [--no-infra|-I] [--observability|-o]"; exit 1 ;;
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
# ═══════════════════════════════════════════════════════════════

echo "🔎 Verificando prerrequisitos..."
PREREQ_FAIL=false

if ! command -v java >/dev/null 2>&1; then
  echo "  ❌ java no encontrado — instala JDK 21+ (sudo pacman -S jdk21-openjdk)"
  PREREQ_FAIL=true
else
  echo "  ✅ java ($(java -version 2>&1 | head -1 | cut -d'"' -f2))"
fi

if [ "$SKIP_BUILD" = "false" ] && [ "$ONLY_FRONTEND" = "false" ]; then
  echo "  ✅ maven ($MAVEN_BIN)"
fi

if [ "$ONLY_BACKEND" = "false" ]; then
  if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
    echo "  ❌ node/npm no encontrados — instala Node.js 18+ (sudo pacman -S nodejs npm)"
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
  pids=$(ss -tlnp 2>/dev/null | grep ":${port} " | grep -oP 'pid=\K[0-9]+' | sort -u || true)
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
declare -A CHILD_PIDS
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
  pkill -f "[j]ava .*aircargo-(gateway|.*-service)-1\.2\.0-SNAPSHOT\.jar" 2>/dev/null || true
  [ "$ONLY_BACKEND" = "false" ] && pkill -f "vite" 2>/dev/null || true
  echo "✅ Limpieza completa"
  exit 0
}

trap cleanup SIGINT SIGTERM EXIT

port_up() { (echo > "/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

docker_ok() { command -v docker >/dev/null 2>&1 && timeout 10 docker info >/dev/null 2>&1; }

wait_health() {
  local name="$1" port="$2" timeout="${3:-240}"
  echo "  ⏳ $name → http://localhost:${port}/actuator/health (timeout ${timeout}s)"
  local start_time=$(date +%s)
  local elapsed=0
  while ! curl -s "http://localhost:${port}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
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
  local cmd="java ${JAVA_OPTS:-} -jar \"$jar\""
  # shellcheck disable=SC2086
  bash -c "$extra_env $cmd" >> "$LOG_DIR/$name.log" 2>&1 &
  local pid=$!
  CHILD_PIDS["$name"]=$pid
  echo "    [PID $pid] → $LOG_DIR/$name.log"

  wait_health "$name" "$port" "$timeout" || return 1
  return 0
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

if [ "$NO_INFRA" = "false" ] && [ "$ONLY_FRONTEND" = "false" ]; then
  pg_up=false; rmq_up=false
  port_up "$PG_PORT" && pg_up=true
  port_up "$RMQ_PORT" && rmq_up=true

  # Si :5432 lo tiene nuestra instancia nativa vacía pero Docker está
  # disponible, detenerla y dejar que Docker sirva la BD real (volumen pgdata)
  if [ "$pg_up" = "true" ] && [ -f "$PG_DATA/PG_VERSION" ] && pg_ctl -D "$PG_DATA" status >/dev/null 2>&1 && docker_ok; then
    echo "🔄 Cediendo :$PG_PORT al Postgres de Docker (deteniendo instancia nativa vacía)..."
    pg_ctl -D "$PG_DATA" -m fast stop || true
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
      if ! command -v initdb >/dev/null 2>&1 || ! command -v pg_ctl >/dev/null 2>&1; then
        echo "❌ Ni Docker ni PostgreSQL nativo están disponibles." >&2
        echo "   Instala PostgreSQL o inicia el daemon de Docker." >&2
        exit 1
      fi
      echo "🐘 Docker no responde — usando PostgreSQL NATIVO (datadir: $PG_DATA)..."
      if [ ! -f "$PG_DATA/PG_VERSION" ]; then
        rm -rf "$PG_DATA"
        mkdir -p "$PG_DATA"
        initdb -D "$PG_DATA" -U "${POSTGRES_USER:-aircargo_user}" -A trust >/dev/null
      fi
      pg_ctl -D "$PG_DATA" -l "$PG_DATA/postgres.log" \
        -o "-p $PG_PORT -c max_connections=150 -c unix_socket_directories=/tmp" -w start
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
#  BUILD (si no --skip-build)
# ═══════════════════════════════════════════════════════════════

if [ "$SKIP_BUILD" = "true" ]; then
  echo "⚡ Flag --skip-build: usando jars existentes (sin recompilar)"
else
  if [ "$ONLY_FRONTEND" = "false" ]; then
    echo "🏗️ Construyendo backend (reactor completo, incluye aircargo-common y feign-clients)..."
    (cd "$AIR_ROOT/backend" && "$MAVEN_BIN" -o install -DskipTests -q) || {
      echo "❌ La compilación del backend ha fallado."
      exit 1
    }
  fi
fi

# ═══════════════════════════════════════════════════════════════
#  BACKEND SERVICES (arranque escalonado con health checks)
# ═══════════════════════════════════════════════════════════════

if [ "$ONLY_FRONTEND" = "false" ]; then
  echo "🚀 Iniciando servicios backend (escalonado con health checks)..."

  # 1) Gateway — todo pasa por él
  start_service "aircargo-gateway" 8080 300 || exit 1

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
    wait  # Espera a que terminen todos los hijos (nunca, salvo señal)
  fi
fi

# ═══════════════════════════════════════════════════════════════
#  FRONTEND (Vite dev server)
# ═══════════════════════════════════════════════════════════════

if [ "$ONLY_BACKEND" = "false" ]; then
  # Idempotencia: si algo ya escucha en :5173, asumimos que es nuestro Vite
  if curl -s http://localhost:5173 2>/dev/null | grep -q "vite\|VITE\|<html"; then
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
    (cd "$AIR_ROOT/frontend" && npm run dev) >> "$LOG_DIR/frontend.log" 2>&1 &
    FRONTEND_PID=$!
    CHILD_PIDS["frontend"]=$FRONTEND_PID
    echo "    [PID $FRONTEND_PID] → $LOG_DIR/frontend.log"

    # Esperar a que Vite esté listo
    FRONTEND_UP=false
    for _ in $(seq 1 30); do
      if curl -s http://localhost:5173 2>/dev/null | grep -q "vite\|VITE\|<html"; then
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
echo "   • Detener todo: Ctrl+C (limpieza garantizada)"
echo ""

# Mantener el script vivo esperando señales
wait
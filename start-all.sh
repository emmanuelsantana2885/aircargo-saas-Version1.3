#!/usr/bin/env bash
set -euo pipefail

# ────────────────────────────────────────────────────────────────
# Aircargo full-stack launcher (backend + frontend)
# ────────────────────────────────────────────────────────────────
#   --skip-build    | -b   → usa jars existentes (arranque rápido)
#   --only-backend  | -B   → solo backend (sin frontend Vite)
#   --only-frontend | -F   → solo frontend (asume backend UP en :8080)
#   --no-infra      | -I   → no tocar Postgres/RabbitMQ (ya corriendo)
# ────────────────────────────────────────────────────────────────

SKIP_BUILD=false
ONLY_BACKEND=false
ONLY_FRONTEND=false
NO_INFRA=false

for arg in "$@"; do
  case "$arg" in
    --skip-build|-b) SKIP_BUILD=true ;;
    --only-backend|-B) ONLY_BACKEND=true ;;
    --only-frontend|-F) ONLY_FRONTEND=true ;;
    --no-infra|-I) NO_INFRA=true ;;
    *) echo "❌ Flag desconocido: $arg"; echo "Uso: $0 [--skip-build|-b] [--only-backend|-B] [--only-frontend|-F] [--no-infra|-I]"; exit 1 ;;
  esac
done

# ── Cargar secrets y validar ───────────────────────────────────
AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$AIR_ROOT/aircargo-env.sh"

PG_PORT="${POSTGRES_PORT:-5432}"
RMQ_PORT="${RABBITMQ_PORT:-5672}"
PG_DATA="$AIR_ROOT/.local-pg"
LOG_DIR="${LOG_DIR:-$HOME/aircargo-logs}"

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
  pkill -f "java -jar.*aircargo.*\.jar" 2>/dev/null || true
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
  if [ "$ONLY_FRONTEND" = "true" ] || ! curl -s http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    if [ "$ONLY_FRONTEND" = "false" ]; then
      echo "⚠️  Gateway no responde en :8080 — asumiendo que ya está corriendo en otro lado"
    fi
  fi

  echo "🌐 Iniciando frontend (Vite dev server en puerto 5173)..."
  mkdir -p "$LOG_DIR"
  (cd "$AIR_ROOT/frontend" && npm run dev) >> "$LOG_DIR/frontend.log" 2>&1 &
  FRONTEND_PID=$!
  CHILD_PIDS["frontend"]=$FRONTEND_PID
  echo "    [PID $FRONTEND_PID] → $LOG_DIR/frontend.log"

  # Esperar a que Vite esté listo
  for _ in $(seq 1 30); do
    if curl -s http://localhost:5173 2>/dev/null | grep -q "vite\|VITE\|<html"; then
      echo "    ✅ Frontend UP"
      break
    fi
    sleep 2
  done
fi

# ═══════════════════════════════════════════════════════════════
#  KEEP ALIVE — el script se queda en foreground hasta Ctrl+C
# ═══════════════════════════════════════════════════════════════

echo ""
echo "🎉 ✅ TODO EN MARCHA !"
echo "   📡 Backend (Gateway) : http://localhost:8080"
[ "$ONLY_BACKEND" = "false" ] && echo "   🌐 Frontend (Vite)    : http://localhost:5173"
echo ""
echo "🛠️  Utilidades:"
echo "   • Logs en tiempo real: tail -f $LOG_DIR/<servicio>.log"
echo "   • Detener todo: Ctrl+C (limpieza garantizada)"
echo ""

# Mantener el script vivo esperando señales
wait
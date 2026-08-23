#!/usr/bin/env bash
set -euo pipefail

# ── Flags ───────────────────────────────────────────────────────
#   --skip-build | -b  →  no recompila, usa los jars existentes (arranque rápido)
SKIP_BUILD=false
case "${1:-}" in
  --skip-build|-b) SKIP_BUILD=true ;;
  "" ) ;;
  *) echo "❌ Flag desconocido: $1  (usa --skip-build / -b)"; exit 1 ;;
esac

# ── Load secrets from gitignored .env ───────────────────────────
AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$AIR_ROOT/aircargo-env.sh"

PG_PORT="${POSTGRES_PORT:-5432}"
RMQ_PORT="${RABBITMQ_PORT:-5672}"
PG_DATA="$AIR_ROOT/.local-pg"

port_up() { (echo > "/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

echo "🛑 Deteniendo procesos existentes..."
pkill -f "java -jar.*aircargo.*\.jar" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true
sleep 2

# ── Infraestructura (Postgres + RabbitMQ) ───────────────────────
pg_up=false; rmq_up=false
port_up "$PG_PORT" && pg_up=true
port_up "$RMQ_PORT" && rmq_up=true

docker_ok() { command -v docker >/dev/null 2>&1 && timeout 10 docker info >/dev/null 2>&1; }

# Si el :5432 lo tiene nuestra instancia NATIVA de fallback pero Docker está
# disponible, detenerla y dejar que Docker sirva la BD real (volumen pgdata
# con los datos históricos — la nativa nace vacía).
if [ "$pg_up" = "true" ] && [ -f "$PG_DATA/PG_VERSION" ] && pg_ctl -D "$PG_DATA" status >/dev/null 2>&1 && docker_ok; then
  echo "🔄 Cediendo :$PG_PORT al Postgres de Docker (deteniendo instancia nativa vacía)..."
  pg_ctl -D "$PG_DATA" -m fast stop || true
  sleep 2
  pg_up=false
fi

# Postgres is MANDATORY — try Docker first, fall back to native Postgres
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

  # Asegurar la base de datos compartida (idempotente)
  PG_DB="${POSTGRES_DB:-aircargo}"
  if command -v psql >/dev/null 2>&1; then
    if ! psql -h 127.0.0.1 -p "$PG_PORT" -U "${POSTGRES_USER:-aircargo_user}" -d postgres -tAc \
         "SELECT 1 FROM pg_database WHERE datname='$PG_DB'" 2>/dev/null | grep -q 1; then
      echo "🗄️  Creando base de datos '$PG_DB'..."
      createdb -h 127.0.0.1 -p "$PG_PORT" -U "${POSTGRES_USER:-aircargo_user}" "$PG_DB"
    fi
  fi
  # Check if RabbitMQ came up too
  port_up "$RMQ_PORT" && rmq_up=true
fi

# RabbitMQ is OPTIONAL — try Docker container, then native daemon
if [ "$rmq_up" = "false" ]; then
  started_rmq=false
  if command -v docker >/dev/null 2>&1 && timeout 10 docker info >/dev/null 2>&1; then
    echo "🐳 Docker disponible — arrancando contenedor RabbitMQ..."
    docker compose -f "$AIR_ROOT/docker/docker-compose.infrastructure.yml" up -d rabbitmq 2>/dev/null || true
    for _ in $(seq 1 20); do
      port_up "$RMQ_PORT" && { started_rmq=true; break; }
      sleep 2
    done
  fi
  if [ "$started_rmq" = "false" ] && command -v rabbitmq-server >/dev/null 2>&1; then
    echo "🐰 Usando RabbitMQ NATIVO..."
    export RABBITMQ_BASE="$HOME/.local-rabbitmq"          # home propio: /var/lib/rabbitmq requiere root
    export CONF_ENV_FILE="$RABBITMQ_BASE/rabbitmq-env.conf" # redirige mnesia/logs fuera de /var/lib
    mkdir -p "$RABBITMQ_BASE"
    rabbitmq-server -detached >/dev/null 2>&1 || true
    # Nota: si el Erlang del sistema es incompatible con esta versión de RabbitMQ
    # (error horus/extraction_denied), falla rápido y se continúa sin broker.
    for _ in $(seq 1 20); do
      port_up "$RMQ_PORT" && { started_rmq=true; break; }
      sleep 2
    done
    if [ "$started_rmq" = "true" ] && command -v rabbitmqctl >/dev/null 2>&1; then
      RMQ_USER="${RABBITMQ_USER:-aircargo}"
      sleep 5  # rabbitmqctl necesita que el nodo termine de registrar el plugin management
      rabbitmqctl add_user "$RMQ_USER" "$RABBITMQ_PASSWORD" >/dev/null 2>&1 || true
      rabbitmqctl change_password "$RMQ_USER" "$RABBITMQ_PASSWORD" >/dev/null 2>&1 || true
      rabbitmqctl set_permissions -p "/" "$RMQ_USER" ".*" ".*" ".*" >/dev/null 2>&1 || true
    fi
  fi
  rmq_up=$started_rmq
fi

if [ "$rmq_up" = "false" ]; then
  echo "⚠️  RabbitMQ(:$RMQ_PORT) no está disponible. notification-service arranca SIN listeners AMQP."
  RABBITMQ_ENABLED=false
else
  RABBITMQ_ENABLED=true
fi

echo "✅ Postgres :$PG_PORT OK $([ "$rmq_up" = "true" ] && echo "| RabbitMQ :$RMQ_PORT OK" || echo '| RabbitMQ: omitido')"

if [ "$SKIP_BUILD" = "true" ]; then
  echo "⚡ Flag --skip-build: usando jars existentes (sin recompilar)"
else
  echo "🏗️ Construyendo backend (reactor completo, incluye aircargo-common y feign-clients)..."
  (cd "$AIR_ROOT/backend" && "$MAVEN_BIN" -o install -DskipTests -q) || {
      echo "❌ La compilación del backend ha fallado."
      exit 1
  }
fi

declare -a services=(
    backend/aircargo-auth-service
    backend/aircargo-flight-service
    backend/aircargo-booking-service
    backend/aircargo-mawb-service
    backend/aircargo-warehouse-service
    backend/aircargo-uld-service
    backend/aircargo-load-planning-service
    backend/aircargo-export-service
    backend/aircargo-notification-service
    backend/aircargo-gateway
)

echo "🚀 Iniciando todos los servicios backend..."
for dir in "${services[@]}"; do
    [ -d "$AIR_ROOT/$dir" ] || { echo "❌ El directorio $dir no existe"; exit 1; }
    name=$(basename "$dir")
    # notification-service uses RABBITMQ_ENABLED env var to conditionally start listeners
    jar="$AIR_ROOT/$dir/target/${name}-1.2.0-SNAPSHOT.jar"
    [ -f "$jar" ] || { echo "❌ No se encontró $jar"; exit 1; }
    echo "  → Iniciando $name"
    # Los logs van al archivo rotado configurado en cada servicio
    # (${LOG_DIR:-~}/aircargo-logs/<name>.log); stdout se descarta.
    (RABBITMQ_ENABLED="$RABBITMQ_ENABLED" java -jar "$jar" > /dev/null 2>&1) &
    echo "    [PID $!] -> ~/aircargo-logs/${name}.log"
done

echo "⏳ Esperando Gateway..."
start_time=$(date +%s)
until curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; do
    sleep 2
    now=$(date +%s)
    if (( now - start_time > 120 )); then
        echo "❌ ERROR: Gateway no responde tras 120s."
        tail -n 30 "${LOG_DIR:-$HOME}/aircargo-logs/gateway.log" 2>/dev/null \
          || tail -n 30 /tmp/aircargo-gateway.log 2>/dev/null || echo "   (no logs disponibles)"
        exit 1
    fi
    echo -n "."
done
echo ""
elapsed=$(($(date +%s) - start_time))
echo "✅ Gateway UP (${elapsed}s)"

echo "🌐 Iniciando frontend (Vite)..."
(cd "$AIR_ROOT/frontend" && npm run dev) &
echo "   → Vite corriendo (puerto 5173)"

echo ""
echo "🎉 ✅ TODO EN MARCHE !"
echo "   📡 Backend  : http://localhost:8080"
echo "   🌐 Frontend : http://localhost:5173"
echo ""
echo "🛠️  Utilitaires rápidos:"
echo "   • restart-all   -> ./start-all.sh"
echo "   • tail-logs     -> tail -f ~/aircargo-logs/(gateway|auth-service|flight-service|booking-service|mawb-service|warehouse-service|uld-service|load-planning-service|export-service|notification-service).log"

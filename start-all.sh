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

echo "🛑 Deteniendo procesos existentes..."
pkill -f "java -jar.*aircargo.*\.jar" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true
sleep 2

# ── Infraestructura (Postgres + RabbitMQ) ───────────────────────
pg_up=false; rmq_up=false
(echo > /dev/tcp/127.0.0.1/5432) 2>/dev/null && pg_up=true
(echo > /dev/tcp/127.0.0.1/5672) 2>/dev/null && rmq_up=true

# Postgres is MANDATORY — try Docker if not running
if [ "$pg_up" = "false" ]; then
  if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    echo "❌ Postgres(:5432) no está corriendo y Docker no está disponible." >&2
    echo "   Inicia Postgres manualmente o instala Docker." >&2
    exit 1
  fi
  echo "🐳 Postgres no detectado — arrancando contenedor..."
  docker compose -f "$AIR_ROOT/docker/docker-compose.infrastructure.yml" up -d postgres 2>/dev/null || \
  docker compose -f "$AIR_ROOT/docker/docker-compose.infrastructure.yml" up -d
  echo "⏳ Esperando Postgres :5432..."
  for _ in $(seq 1 30); do
    (echo > /dev/tcp/127.0.0.1/5432) 2>/dev/null && { pg_up=true; break; }
    sleep 2
  done
  [ "$pg_up" = "true" ] || { echo "❌ Postgres no responde en :5432"; exit 1; }
  # Check if RabbitMQ came up too
  (echo > /dev/tcp/127.0.0.1/5672) 2>/dev/null && rmq_up=true
fi

# RabbitMQ is OPTIONAL — notification-service starts regardless but with/without listeners
if [ "$rmq_up" = "false" ]; then
  echo "⚠️  RabbitMQ(:5672) no está disponible. notification-service arranca SIN listeners AMQP."
  echo "   Para habilitarlo, instala y arranca RabbitMQ en :5672."
  RABBITMQ_ENABLED=false
else
  RABBITMQ_ENABLED=true
fi

echo "✅ Postgres :5432 OK $([ "$rmq_up" = "true" ] && echo '| RabbitMQ :5672 OK' || echo '| RabbitMQ: omitido')"

if [ "$SKIP_BUILD" = "true" ]; then
  echo "⚡ Flag --skip-build: usando jars existentes (sin recompilar)"
else
  echo "🏗️ Construyendo backend (reactor completo, incluye aircargo-common y feign-clients)..."
  (cd "$AIR_ROOT/backend" && "$MAVEN_BIN" install -DskipTests -q) || {
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
    (RABBITMQ_ENABLED="$RABBITMQ_ENABLED" java -jar "$jar" >> "/tmp/${name}.log" 2>&1) &
    echo "    [PID $!] -> /tmp/${name}.log"
done

echo "⏳ Esperando Gateway..."
start_time=$(date +%s)
until curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; do
    sleep 2
    now=$(date +%s)
    if (( now - start_time > 90 )); then
        echo "❌ ERROR: Gateway no responde tras 90s."
        tail -n 30 /tmp/aircargo-gateway.log 2>/dev/null || echo "   (no logs disponibles)"
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
echo "   • tail-logs     -> tail -f /tmp/(gateway|auth|flight|booking|mawb|warehouse|uld|load-planning|export|notification).log"

#!/usr/bin/env bash
set -euo pipefail

# ── Load secrets from gitignored .env ───────────────────────────
AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$AIR_ROOT/aircargo-env.sh"

# =============================================================================
# 🛑 STOP EVERYTHING RUNNING — force an exec clean slate
# =============================================================================
echo "[🛑 Stopping any existing Java/Vite processes...]"
pkill -f "aircargo.*SNAPSHOT.jar" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true
sleep 3
pkill -9 -f "aircargo.*SNAPSHOT.jar" 2>/dev/null || true
sleep 2

# =============================================================================
# 🚀 BUILD every backend module (from the backend aggregator POM)
#    Heap-bounded so t3.small (1.9GB) doesn't swap-death during the build.
# =============================================================================
echo "[🏗️  Building all backend modules ...]"

MAVEN_OPTS="${MAVEN_OPTS:--Xmx512m -XX:MaxMetaspaceSize=256m}"

(cd "$AIR_ROOT/backend" && MAVEN_OPTS="$MAVEN_OPTS" "$MAVEN_BIN" clean package -DskipTests -q) || {
    echo "❌ Maven build failed. Aborting."
    exit 1
}

echo "[✅ Build finished.]"

# =============================================================================
# 🎮 START services ONE AT A TIME, waiting for each health endpoint.
#    Staggered boot prevents the JVM boot-storm that froze the t3.small.
# =============================================================================
echo "[▶️  Launching all backend services (staggered) ...]"

MAX_CONNS="-Dspring.datasource.hikari.maximum-pool-size=3"
JAVA_OPTS="${JAVA_OPTS:-}"

wait_health() {
    local name="$1" port="$2" timeout="${3:-240}"
    echo "  ⏳ $name → http://localhost:${port}/actuator/health (timeout ${timeout}s)"
    local start_time=$(date +%s)
    local elapsed=0
    while ! curl -s "http://localhost:${port}/actuator/health" | grep -q '"status":"UP"'; do
        elapsed=$(( $(date +%s) - start_time ))
        if [ "$elapsed" -gt "$timeout" ]; then
            echo "    ❌ $name not healthy after ${timeout}s."
            return 1
        fi
        sleep 3
    done
    if [ "$elapsed" -eq 0 ]; then
        elapsed=$(( $(date +%s) - start_time ))
    fi
    echo "    ✅ $name UP (${elapsed}s)"
}

# 1) Gateway — everything routes through it
(
    cd "$AIR_ROOT/backend/aircargo-gateway"
    java $MAX_CONNS $JAVA_OPTS -jar "target/aircargo-gateway-1.2.0-SNAPSHOT.jar" >> "/tmp/aircargo-gateway.log" 2>&1 &
)
wait_health aircargo-gateway 8080 300 || exit 1

# 2) Auth — needed for JWT issuance used by everyone downstream
(
    cd "$AIR_ROOT/backend/aircargo-auth-service"
    java $MAX_CONNS $JAVA_OPTS -jar "target/aircargo-auth-service-1.2.0-SNAPSHOT.jar" >> "/tmp/aircargo-auth-service.log" 2>&1 &
)
wait_health aircargo-auth-service 9092 240 || exit 1

# 3) The remaining 8 — booted sequentially so they don't collide on CPU/mem
# Detect RabbitMQ for notification-service
RABBITMQ_ENABLED=true
(echo > /dev/tcp/127.0.0.1/5672) 2>/dev/null || {
    echo "  ⚠️  RabbitMQ(:5672) no disponible — notification-service arranca SIN listeners AMQP"
    RABBITMQ_ENABLED=false
}

for entry in "aircargo-flight-service 9093" \
             "aircargo-booking-service 9094" \
             "aircargo-mawb-service 9095" \
             "aircargo-warehouse-service 9096" \
             "aircargo-uld-service 9097" \
             "aircargo-load-planning-service 9098" \
             "aircargo-export-service 9099" \
             "aircargo-notification-service 9100"; do
    name="${entry%% *}"
    port="${entry##* }"
    (
        cd "$AIR_ROOT/backend/$name"
        RABBITMQ_ENABLED="$RABBITMQ_ENABLED" java $MAX_CONNS $JAVA_OPTS -jar "target/${name}-1.2.0-SNAPSHOT.jar" >> "/tmp/${name}.log" 2>&1 &
    )
    wait_health "$name" "$port" 240 || exit 1
done

echo "✅ All 10 backend services healthy. (logs in /tmp/<service>.log)"

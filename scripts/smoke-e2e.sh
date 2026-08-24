#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════
# SMOKE TEST E2E — batería mínima para declarar el stack "entregable"
#
#   ./scripts/smoke-e2e.sh            # usa credenciales del .env (seed admin)
#   ADMIN_EMAIL=x@y.z ADMIN_PASS=… ./scripts/smoke-e2e.sh
#
# Sale con código 0 solo si TODAS las comprobaciones pasan.
# ══════════════════════════════════════════════════════════════
set -uo pipefail
AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$AIR_ROOT/.env" 2>/dev/null

GATEWAY="http://localhost:8080"
PASS=0; FAIL=0
ok(){ echo "  ✓ $1"; PASS=$((PASS+1)); }
bad(){ echo "  ✗ $1"; FAIL=$((FAIL+1)); }
check(){ local desc="$1" expected="$2" actual="$3"; [ "$actual" = "$expected" ] && ok "$desc → $actual" || bad "$desc → esperado $expected, recibido $actual"; }

echo "── 1. Infraestructura ──────────────────────────"
for p in 5432 8080; do
  ss -tln | grep -q ":$p " && ok "puerto $p escuchando" || bad "puerto $p NO escucha"
done

echo "── 2. Salud de servicios (vía gateway) ─────────"
for s in auth flight booking mawb warehouse uld load-planning export notification; do
  case $s in
    auth) port=9092;; flight) port=9093;; booking) port=9094;; mawb) port=9095;;
    warehouse) port=9096;; uld) port=9097;; load-planning) port=9098;;
    export) port=9099;; notification) port=9100;;
  esac
  code=$(curl -s -m 4 -o /dev/null -w "%{http_code}" http://localhost:$port/actuator/health)
  check "health $s" 200 "$code"
done

echo "── 3. Autenticación ────────────────────────────"
# 3a. credenciales inválidas → 401 genérico (nunca 500 ni mensaje revelador)
code=$(curl -s -m 5 -o /dev/null -w "%{http_code}" -X POST $GATEWAY/api/auth/login \
  -H "Content-Type: application/json" -d '{"email":"smoke@invalid","password":"WrongPass1!"}')
check "login inválido" 401 "$code"

# 3b. sin autenticación en endpoints protegidos → 401 (NUNCA 403: rompería refresh)
for ep in /api/users /api/flights/list /api/ulds /api/mawbs; do
  code=$(curl -s -m 4 -o /dev/null -w "%{http_code}" $GATEWAY$ep)
  check "anónimo $ep" 401 "$code"
done

# 3c. login real (requiere usuario con contraseña definida)
if [ -n "${ADMIN_EMAIL:-}" ] && [ -n "${ADMIN_PASS:-}" ]; then
  rm -f /tmp/smoke.jar
  code=$(curl -s -m 8 -c /tmp/smoke.jar -o /dev/null -w "%{http_code}" -X POST $GATEWAY/api/auth/login \
    -H "Content-Type: application/json" -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASS\"}")
  check "login admin" 200 "$code"
  grep -q aircargo_at /tmp/smoke.jar && ok "cookies httpOnly emitidas" || bad "sin cookies de sesión"

  echo "── 4. Acceso autorizado solo con cookie ────────"
  for ep in /api/users /api/auth/me; do
    code=$(curl -s -m 5 -b /tmp/smoke.jar -o /dev/null -w "%{http_code}" $GATEWAY$ep)
    check "cookie → $ep" 200 "$code"
  done
  # sin cookie pero con perfil viejo en localStorage simulado → debe seguir 401
  code=$(curl -s -m 4 -o /dev/null -w "%{http_code}" $GATEWAY/api/users)
  check "sin cookie sigue 401" 401 "$code"
else
  echo "  ⚠ Salteando login real: define ADMIN_EMAIL y ADMIN_PASS para probarlo"
fi

echo "── 5. Frontend ─────────────────────────────────"
code=$(curl -s -m 4 -o /dev/null -w "%{http_code}" http://localhost:5173/)
check "Vite dev server" 200 "$code"

echo ""
echo "═══════════════════════════════════════════════"
[ $FAIL -eq 0 ] && echo "✅ SMOKE OK — $PASS comprobaciones, 0 fallos. ENTREGABLE." \
                  && exit 0
echo "❌ SMOKE FALLIDO — $FAIL fallos de $((PASS+FAIL))"
exit 1

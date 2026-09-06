#!/usr/bin/env bash
# Quick verification script for Aircargo production deployment
# Run after deploy to validate everything is working

set -euo pipefail

NAMESPACE="aircargo"
GATEWAY_HOST="${GATEWAY_HOST:-api.yourdomain.com}"
FRONTEND_HOST="${FRONTEND_HOST:-yourdomain.com}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓${NC} $1"; }
fail() { echo -e "${RED}✗${NC} $1"; exit 1; }
warn() { echo -e "${YELLOW}⚠${NC} $1"; }

echo "=== Aircargo Production Deployment Verification ==="
echo "Namespace: $NAMESPACE"
echo "Gateway: https://$GATEWAY_HOST"
echo "Frontend: https://$FRONTEND_HOST"
echo ""

# 1. Check all pods running
echo "1. Checking pod status..."
NOT_READY=$(kubectl get pods -n "$NAMESPACE" --no-headers 2>/dev/null | awk '$3!="Running" && $3!="Completed" {print $1" "$3}')
if [[ -z "$NOT_READY" ]]; then
  pass "All pods Running/Completed"
else
  fail "Pods not ready:\n$NOT_READY"
fi

# 2. Check secrets exist
echo "2. Checking secrets..."
if kubectl get secret aircargo-secrets -n "$NAMESPACE" >/dev/null 2>&1; then
  pass "Secret 'aircargo-secrets' exists"
  # Verify required keys
  for key in JWT_SECRET POSTGRES_PASSWORD RABBITMQ_PASSWORD APP_ENCRYPTION_KEY SMTP_HOST SMTP_PASSWORD; do
    if kubectl get secret aircargo-secrets -n "$NAMESPACE" -o jsonpath="{.data.$key}" | base64 -d >/dev/null 2>&1; then
      pass "  $key present"
    else
      warn "  $key missing or empty"
    fi
  done
else
  fail "Secret 'aircargo-secrets' not found"
fi

# 3. Check TLS certificates
echo "3. Checking TLS certificates..."
for cert in aircargo-gateway-tls aircargo-frontend-tls; do
  if kubectl get secret "$cert" -n "$NAMESPACE" >/dev/null 2>&1; then
    pass "TLS secret '$cert' exists"
  else
    warn "TLS secret '$cert' not found (cert-manager may still be issuing)"
  fi
done

# 4. Check cert-manager certificates
echo "4. Checking cert-manager Certificate resources..."
kubectl get certificates -n "$NAMESPACE" --no-headers 2>/dev/null | while read name ready secret age; do
  if [[ "$ready" == "True" ]]; then
    pass "Certificate '$name' ready"
  else
    warn "Certificate '$name' not ready: $ready"
  fi
done

# 5. Health checks
echo "5. Checking health endpoints..."
if curl -sf "https://$GATEWAY_HOST/actuator/health" | grep -q '"status":"UP"'; then
  pass "Gateway health UP"
else
  fail "Gateway health check failed"
fi

if curl -sf "https://$FRONTEND_HOST/" | grep -qi "aircargo\|vue\|app"; then
  pass "Frontend loads"
else
  fail "Frontend check failed"
fi

# 6. Check individual service health (via gateway)
echo "6. Checking service health via gateway..."
TOKEN=$(curl -s -X POST "https://$GATEWAY_HOST/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@yourdomain.com"}' 2>/dev/null | jq -r .token 2>/dev/null || echo "")

if [[ -n "$TOKEN" && "$TOKEN" != "null" ]]; then
  pass "Auth login works (got token)"
  
  for endpoint in users flights mawbs bookings ulds receipts; do
    if curl -sf -H "Authorization: Bearer $TOKEN" "https://$GATEWAY_HOST/api/$endpoint" >/dev/null; then
      pass "  /api/$endpoint accessible"
    else
      warn "  /api/$endpoint failed"
    fi
  done
else
  warn "Auth login failed (check admin user exists)"
fi

# 7. Check Redis (if HA enabled)
echo "7. Checking Redis..."
if kubectl get deploy redis -n "$NAMESPACE" >/dev/null 2>&1; then
  if kubectl get pods -n "$NAMESPACE" -l app=redis --no-headers | grep -q Running; then
    pass "Redis pod running"
    if kubectl exec -n "$NAMESPACE" deploy/redis -- redis-cli ping 2>/dev/null | grep -q PONG; then
      pass "Redis responding to PING"
    else
      warn "Redis not responding to PING"
    fi
  else
    warn "Redis pod not running"
  fi
else
  pass "Redis not deployed (HA disabled)"
fi

# 8. Check RabbitMQ
echo "8. Checking RabbitMQ..."
if kubectl get pods -n "$NAMESPACE" -l app=rabbitmq --no-headers | grep -q Running; then
  pass "RabbitMQ pod running"
else
  warn "RabbitMQ pod not running"
fi

# 9. Check PostgreSQL
echo "9. Checking PostgreSQL..."
if kubectl get pods -n "$NAMESPACE" -l app=postgres --no-headers | grep -q Running; then
  pass "PostgreSQL pod running"
else
  warn "PostgreSQL pod not running (may be external)"
fi

# 10. Check monitoring
echo "10. Checking Prometheus metrics..."
if curl -sf "https://$GATEWAY_HOST/actuator/prometheus" | grep -q "jvm_memory_used_bytes"; then
  pass "Prometheus metrics exposed"
else
  warn "Prometheus metrics not accessible"
fi

# 11. Check backup cronjob
echo "11. Checking backup CronJob..."
if kubectl get cronjob aircargo-backup -n "$NAMESPACE" >/dev/null 2>&1; then
  pass "Backup CronJob exists"
  LAST_SCHEDULE=$(kubectl get cronjob aircargo-backup -n "$NAMESPACE" -o jsonpath='{.status.lastScheduleTime}')
  if [[ -n "$LAST_SCHEDULE" ]]; then
    pass "  Last scheduled: $LAST_SCHEDULE"
  else
    warn "  Never scheduled yet"
  fi
else
  warn "Backup CronJob not found"
fi

echo ""
echo "=== Verification Complete ==="
echo ""
echo "Next steps if all passed:"
echo "  - Configure monitoring alerts (see PRODUCTION_DEPLOYMENT_RUNBOOK.md)"
echo "  - Run load test: ./scripts/load-test.sh"
echo "  - Schedule penetration test"
echo "  - Document runbook for team"
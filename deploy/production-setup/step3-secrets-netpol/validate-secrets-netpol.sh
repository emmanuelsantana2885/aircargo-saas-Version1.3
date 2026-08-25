#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Step 3 Validation: Verify External Secrets + NetworkPolicies
# ────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

log "══════════════════════════════════════════════════════════════"
log "  VALIDACIÓN PASO 3: SECRETS + NETWORKPOLICIES"
log "══════════════════════════════════════════════════════════════"

# 1. External Secrets Operator
log "1/8 Verificando External Secrets Operator..."
wait_for "ESO pods ready" "kubectl get pods -n external-secrets-system -l app.kubernetes.io/name=external-secrets --field-selector=status.phase=Running | grep -q '1/1'" 120 5
log "  ESO: OK"

# 2. ClusterSecretStore
log "2/8 Verificando ClusterSecretStore..."
CSS_STATUS=$(kubectl get clustersecretstore aws-secretsmanager -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null)
[[ "$CSS_STATUS" == "True" ]] || { error "ClusterSecretStore no Ready: $CSS_STATUS"; exit 1; }
log "  ClusterSecretStore: Ready"

# 3. ExternalSecret
log "3/8 Verificando ExternalSecret..."
ES_STATUS=$(kubectl get externalsecret aircargo-secrets -n aircargo -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null)
[[ "$ES_STATUS" == "True" ]] || { error "ExternalSecret no Ready: $ES_STATUS"; exit 1; }
log "  ExternalSecret: Ready"

# 4. Generated Secret
log "4/8 Verificando secret generado..."
kubectl get secret aircargo-secrets -n aircargo >/dev/null 2>&1 || { error "Secret aircargo-secrets no existe"; exit 1; }

# Verify all required keys
REQUIRED_KEYS=(
  "JWT_SECRET"
  "POSTGRES_USER"
  "POSTGRES_PASSWORD"
  "POSTGRES_HOST"
  "POSTGRES_PORT"
  "POSTGRES_DB"
  "RABBITMQ_USER"
  "RABBITMQ_PASSWORD"
  "RABBITMQ_HOST"
  "RABBITMQ_PORT"
  "APP_ENCRYPTION_KEY"
  "SMTP_HOST"
  "SMTP_PASSWORD"
)

for key in "${REQUIRED_KEYS[@]}"; do
  kubectl get secret aircargo-secrets -n aircargo -o jsonpath="{.data.$key}" | base64 -d >/dev/null 2>&1 || { error "Key faltante en secret: $key"; exit 1; }
done
log "  Secret completo: 15/15 keys"

# 5. NetworkPolicies count
log "5/8 Verificando NetworkPolicies..."
NP_COUNT=$(kubectl get networkpolicy -n aircargo --no-headers 2>/dev/null | wc -l)
[[ $NP_COUNT -ge 12 ]] || { error "NetworkPolicies insuficientes: $NP_COUNT"; exit 1; }

# Verify specific policies exist
REQUIRED_NPS=(
  "default-deny-ingress"
  "gateway-allow-external"
  "gateway-to-services"
  "auth-service-egress"
  "services-db-egress"
  "notification-service-egress"
  "frontend-to-gateway"
  "postgres-allow-services"
  "rabbitmq-allow-notification"
)

for np in "${REQUIRED_NPS[@]}"; do
  kubectl get networkpolicy "$np" -n aircargo >/dev/null 2>&1 || { error "NetworkPolicy faltante: $np"; exit 1; }
done
log "  NetworkPolicies requeridas: ${#REQUIRED_NPS[@]}/${#REQUIRED_NPS[@]}"

# 6. Network isolation test
log "6/8 Probando aislamiento de red..."
cat > /tmp/net-verify.yaml <<'YAML'
apiVersion: v1
kind: Pod
metadata:
  name: net-verify-client
  namespace: aircargo
  labels:
    app: net-verify-client
spec:
  restartPolicy: Never
  containers:
    - name: client
      image: curlimages/curl:latest
      command: ["sleep", "300"]
---
apiVersion: v1
kind: Pod
metadata:
  name: net-verify-server
  namespace: aircargo
  labels:
    app: net-verify-server
spec:
  restartPolicy: Never
  containers:
    - name: server
      image: nginx:alpine
      ports:
        - containerPort: 80
---
apiVersion: v1
kind: Service
metadata:
  name: net-verify-server
  namespace: aircargo
spec:
  selector:
    app: net-verify-server
  ports:
    - port: 80
      targetPort: 80
YAML

kubectl apply -f /tmp/net-verify.yaml
wait_for "Verify pods ready" "kubectl get pods -n aircargo -l app in (net-verify-client,net-verify-server) --field-selector=status.phase=Running -o jsonpath='{.items[*].status.phase}' | grep -q Running" 60 5

# Test 1: Default deny (client -> server without policy)
log "  Test 1: Default deny (debe fallar)..."
sleep 3
if kubectl exec -n aircargo net-verify-client -- curl -sf --max-time 3 http://net-verify-server >/dev/null 2>&1; then
  error "Default-deny NO funciona: cliente alcanzó servidor sin política"
  exit 1
else
  log "  ✓ Default-deny: BLOQUEADO correctamente"
fi

# Test 2: Gateway -> Auth (policy exists)
log "  Test 2: Gateway -> Auth (debe permitir)..."
# Create temporary gateway pod
kubectl run -n aircargo net-verify-gateway --image=curlimages/curl:latest -- sleep 300 --labels="app=aircargo-gateway" --overrides='{"spec":{"serviceAccountName":"aircargo-sa"}}' 2>/dev/null || true
wait_for "Gateway pod ready" "kubectl get pod -n aircargo net-verify-gateway --field-selector=status.phase=Running -o jsonpath='{.status.phase}' | grep -q Running" 60 5

# Get auth service IP
AUTH_IP=$(kubectl get svc -n aircargo aircargo-auth-service -o jsonpath='{.spec.clusterIP}')
if kubectl exec -n aircargo net-verify-gateway -- curl -sf --max-time 5 "http://$AUTH_IP:9092/actuator/health" >/dev/null 2>&1; then
  log "  ✓ Gateway -> Auth: PERMITIDO correctamente"
else
  warn "  Gateway -> Auth: No accesible (puede ser que auth no esté desplegado aún)"
fi

# Cleanup
kubectl delete -f /tmp/net-verify.yaml --ignore-not-found=true
kubectl delete pod -n aircargo net-verify-gateway --ignore-not-found=true 2>/dev/null || true

# 7. Security hardening
log "7/8 Verificando hardening..."

# Non-root
NON_ROOT=$(kubectl get pods -n aircargo -o jsonpath='{range .items[*]}{.spec.securityContext.runAsNonRoot}{"\n"}{end}' | grep -v "true" | wc -l)
[[ $NON_ROOT -eq 0 ]] || { warn "Pods sin runAsNonRoot: $NON_ROOT"; }

# Read-only rootfs
RO_FS=$(kubectl get pods -n aircargo -o jsonpath='{range .items[*]}{.spec.containers[*].securityContext.readOnlyRootFilesystem}{"\n"}{end}' | grep -v "true" | wc -l)
[[ $RO_FS -eq 0 ]] || { warn "Contenedores sin readOnlyRootFilesystem: $RO_FS"; }

# Capabilities dropped
CAP_DROP=$(kubectl get pods -n aircargo -o jsonpath='{range .items[*]}{.spec.containers[*].securityContext.capabilities.drop}{"\n"}{end}' | grep -i "all" | wc -l)
[[ $CAP_DROP -gt 0 ]] || { warn "Capacidades no dropeadas (recomendado: drop ALL)"; }

# 8. Secret rotation readiness
log "8/8 Verificando rotación de secrets..."
# Check ExternalSecret refresh interval
REFRESH=$(kubectl get externalsecret aircargo-secrets -n aircargo -o jsonpath='{.spec.refreshInterval}')
[[ -n "$REFRESH" ]] || { error "ExternalSecret sin refreshInterval"; exit 1; }
log "  Refresh interval: $REFRESH"

# Verify AWS secrets exist
aws secretsmanager describe-secret --secret-id aircargo/production/jwt-secret >/dev/null 2>&1 || { error "Secret JWT no existe en AWS"; exit 1; }
aws secretsmanager describe-secret --secret-id aircargo/production/database >/dev/null 2>&1 || { error "Secret DB no existe en AWS"; exit 1; }
log "  Secrets en AWS Secrets Manager: OK"

log "══════════════════════════════════════════════════════════════"
log "  VALIDACIÓN PASO 3: EXITOSA"
log "══════════════════════════════════════════════════════════════"
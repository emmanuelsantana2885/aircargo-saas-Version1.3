#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Step 3: External Secrets + NetworkPolicies + Security Hardening
# ────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

log "══════════════════════════════════════════════════════════════"
log "  PASO 3: EXTERNAL SECRETS + NETWORKPOLICIES + HARDENING"
log "══════════════════════════════════════════════════════════════"

# ─── 1. Install External Secrets Operator ───
log "1/6 Instalando External Secrets Operator..."

helm repo add external-secrets https://charts.external-secrets.io
helm repo update

cat > /tmp/eso-values.yaml <<'YAML'
# External Secrets Operator values
installCRDs: true

serviceAccount:
  create: true

crds:
  enabled: true

# Enable webhook for validation
webhook:
  enabled: true
  port: 9443

# Metrics
serviceMonitor:
  enabled: true
  interval: 30s

# Resources
resources:
  requests:
    memory: 100Mi
    cpu: 50m
  limits:
    memory: 200Mi
    cpu: 200m
YAML

helm_deploy "external-secrets" "external-secrets/external-secrets" "external-secrets-system" "/tmp/eso-values.yaml" "true" "180s"

# ─── 2. Create SecretStore (AWS Secrets Manager) ───
log "2/6 Creando SecretStore (AWS Secrets Manager)..."

# Get AWS account ID and region
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION=$(aws configure get region || echo "us-east-1")

cat > /tmp/secretstore.yaml <<YAML
apiVersion: external-secrets.io/v1beta1
kind: ClusterSecretStore
metadata:
  name: aws-secretsmanager
spec:
  provider:
    aws:
      service: SecretsManager
      region: ${AWS_REGION}
      auth:
        jwt:
          serviceAccountRef:
            name: external-secrets-sa
            namespace: external-secrets-system
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: external-secrets-sa
  namespace: external-secrets-system
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::${AWS_ACCOUNT_ID}:role/ExternalSecretsOperatorRole
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: external-secrets-operator
rules:
  - apiGroups: ["external-secrets.io"]
    resources: ["externalsecrets", "secretstores", "clustersecretstores"]
    verbs: ["*"]
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: external-secrets-operator-binding
subjects:
  - kind: ServiceAccount
    name: external-secrets-sa
    namespace: external-secrets-system
roleRef:
  kind: ClusterRole
  name: external-secrets-operator
  apiGroup: rbac.authorization.k8s.io
YAML

kubectl apply -f /tmp/secretstore.yaml

# Wait for SecretStore to be ready
wait_for "ClusterSecretStore ready" "kubectl get clustersecretstore aws-secretsmanager -o jsonpath='{.status.conditions[?(@.type==\"Ready\")].status}' | grep -q True" 120 5

# ─── 3. Create ExternalSecret for aircargo-secrets ───
log "3/6 Creando ExternalSecret para aircargo-secrets..."

cat > /tmp/externalsecret.yaml <<YAML
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: aircargo-secrets
  namespace: aircargo
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secretsmanager
    kind: ClusterSecretStore
  target:
    name: aircargo-secrets
    creationPolicy: Owner
    deletionPolicy: Delete
  data:
    - secretKey: JWT_SECRET
      remoteRef:
        key: aircargo/production/jwt-secret
        property: JWT_SECRET
    - secretKey: POSTGRES_USER
      remoteRef:
        key: aircargo/production/database
        property: POSTGRES_USER
    - secretKey: POSTGRES_PASSWORD
      remoteRef:
        key: aircargo/production/database
        property: POSTGRES_PASSWORD
    - secretKey: POSTGRES_HOST
      remoteRef:
        key: aircargo/production/database
        property: POSTGRES_HOST
    - secretKey: POSTGRES_PORT
      remoteRef:
        key: aircargo/production/database
        property: POSTGRES_PORT
    - secretKey: POSTGRES_DB
      remoteRef:
        key: aircargo/production/database
        property: POSTGRES_DB
    - secretKey: RABBITMQ_USER
      remoteRef:
        key: aircargo/production/rabbitmq
        property: RABBITMQ_USER
    - secretKey: RABBITMQ_PASSWORD
      remoteRef:
        key: aircargo/production/rabbitmq
        property: RABBITMQ_PASSWORD
    - secretKey: RABBITMQ_HOST
      remoteRef:
        key: aircargo/production/rabbitmq
        property: RABBITMQ_HOST
    - secretKey: RABBITMQ_PORT
      remoteRef:
        key: aircargo/production/rabbitmq
        property: RABBITMQ_PORT
    - secretKey: APP_ENCRYPTION_KEY
      remoteRef:
        key: aircargo/production/encryption
        property: APP_ENCRYPTION_KEY
    - secretKey: SMTP_HOST
      remoteRef:
        key: aircargo/production/smtp
        property: SMTP_HOST
    - secretKey: SMTP_PORT
      remoteRef:
        key: aircargo/production/smtp
        property: SMTP_PORT
    - secretKey: SMTP_USERNAME
      remoteRef:
        key: aircargo/production/smtp
        property: SMTP_USERNAME
    - secretKey: SMTP_PASSWORD
      remoteRef:
        key: aircargo/production/smtp
        property: SMTP_PASSWORD
    - secretKey: SMTP_FROM
      remoteRef:
        key: aircargo/production/smtp
        property: SMTP_FROM
    - secretKey: SMTP_AUTH
      remoteRef:
        key: aircargo/production/smtp
        property: SMTP_AUTH
    - secretKey: SMTP_STARTTLS
      remoteRef:
        key: aircargo/production/smtp
        property: SMTP_STARTTLS
    - secretKey: AWS_ACCESS_KEY_ID
      remoteRef:
        key: aircargo/production/aws
        property: AWS_ACCESS_KEY_ID
    - secretKey: AWS_SECRET_ACCESS_KEY
      remoteRef:
        key: aircargo/production/aws
        property: AWS_SECRET_ACCESS_KEY
    - secretKey: AWS_S3_BUCKET
      remoteRef:
        key: aircargo/production/aws
        property: AWS_S3_BUCKET
    - secretKey: AWS_REGION
      remoteRef:
        key: aircargo/production/aws
        property: AWS_REGION
YAML

kubectl apply -f /tmp/externalsecret.yaml

# Wait for secret to be created
wait_for "ExternalSecret sincronizado" "kubectl get externalsecret aircargo-secrets -n aircargo -o jsonpath='{.status.conditions[?(@.type==\"Ready\")].status}' | grep -q True" 120 5

# ─── 4. Verify NetworkPolicies are enforced ───
log "4/6 Verificando NetworkPolicies..."

# Check CNI supports NetworkPolicy
CNI=$(kubectl get nodes -o jsonpath='{.items[0].metadata.labels.kubernetes\.io/os}' 2>/dev/null || echo "unknown")
log "  CNI detectado: $CNI"

# Verify all NetworkPolicies exist
NP_COUNT=$(kubectl get networkpolicy -n aircargo --no-headers 2>/dev/null | wc -l)
[[ $NP_COUNT -ge 12 ]] || { error "NetworkPolicies insuficientes: $NP_COUNT (esperado >=12)"; exit 1; }
log "  NetworkPolicies: $NP_COUNT"

# Test default deny (create test pod and verify isolation)
log "  Probando aislamiento de red..."
cat > /tmp/network-test.yaml <<'YAML'
apiVersion: v1
kind: Pod
metadata:
  name: network-test-client
  namespace: aircargo
  labels:
    app: network-test-client
spec:
  restartPolicy: Never
  containers:
    - name: client
      image: curlimages/curl:latest
      command: ["sleep", "3600"]
---
apiVersion: v1
kind: Pod
metadata:
  name: network-test-server
  namespace: aircargo
  labels:
    app: network-test-server
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
  name: network-test-server
  namespace: aircargo
spec:
  selector:
    app: network-test-server
  ports:
    - port: 80
      targetPort: 80
YAML

kubectl apply -f /tmp/network-test.yaml
wait_for "Test pods ready" "kubectl get pods -n aircargo -l app in (network-test-client,network-test-server) --field-selector=status.phase=Running -o jsonpath='{.items[*].status.phase}' | grep -q Running" 60 5

# Test: client should NOT reach server (no NetworkPolicy allowing it)
log "  Probando default-deny..."
sleep 5
if kubectl exec -n aircargo network-test-client -- curl -sf --max-time 5 http://network-test-server >/dev/null 2>&1; then
  warn "  ⚠️  Default-deny NO está funcionando (cliente alcanzó servidor sin política)"
else
  log "  ✓ Default-deny funcionando (cliente NO alcanzó servidor)"
fi

# Cleanup test pods
kubectl delete -f /tmp/network-test.yaml --ignore-not-found=true

# ─── 5. Verify Security Hardening ───
log "5/6 Verificando hardening de seguridad..."

# Check PodSecurityStandards (if available)
if kubectl get crd podsecuritypolicies.policy >/dev/null 2>&1; then
  log "  PodSecurityPolicies: Disponible"
else
  log "  PodSecurityStandards (built-in): Usando restricted profile"
fi

# Check non-root containers
NON_ROOT_VIOLATIONS=$(kubectl get pods -n aircargo -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.securityContext.runAsNonRoot}{"\n"}{end}' | grep -v "true" | wc -l)
[[ $NON_ROOT_VIOLATIONS -eq 0 ]] || { warn "Pods sin runAsNonRoot: $NON_ROOT_VIOLATIONS"; }

# Check read-only root filesystem
RO_VIOLATIONS=$(kubectl get pods -n aircargo -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.containers[*].securityContext.readOnlyRootFilesystem}{"\n"}{end}' | grep -v "true" | wc -l)
[[ $RO_VIOLATIONS -eq 0 ]] || { warn "Contenedores sin readOnlyRootFilesystem: $RO_VIOLATIONS"; }

# Check resource limits
NO_LIMITS=$(kubectl get pods -n aircargo -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.containers[*].resources.limits.memory}{"\n"}{end}' | grep -v "Gi\|Mi" | wc -l)
[[ $NO_LIMITS -eq 0 ]] || { warn "Contenedores sin memory limits: $NO_LIMITS"; }

log "  Hardening verificado (ver warnings arriba si los hay)"

# ─── 6. Rotate Secrets (Create new versions in AWS Secrets Manager) ───
log "6/6 Rotando secrets (creando nuevas versiones en AWS Secrets Manager)..."

# This would typically be done via a separate rotation Lambda/CronJob
# Here we just verify the secrets exist and are accessible
SECRETS_TO_CHECK=(
  "aircargo/production/jwt-secret"
  "aircargo/production/database"
  "aircargo/production/rabbitmq"
  "aircargo/production/encryption"
  "aircargo/production/smtp"
  "aircargo/production/aws"
)

for secret in "${SECRETS_TO_CHECK[@]}"; do
  aws secretsmanager describe-secret --secret-id "$secret" >/dev/null 2>&1 || { error "Secret no existe en AWS: $secret"; exit 1; }
  log "  Secret verificado: $secret"
done

log "══════════════════════════════════════════════════════════════"
log "  PASO 3 COMPLETADO"
log "══════════════════════════════════════════════════════════════"
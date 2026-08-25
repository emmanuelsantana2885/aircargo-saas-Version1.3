#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Step 1 Validation: Verify Infrastructure Provisioning
# ────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

TF_DIR="$SCRIPT_DIR/terraform"

log "══════════════════════════════════════════════════════════════"
log "  VALIDACIÓN PASO 1: INFRAESTRUCTURA"
log "══════════════════════════════════════════════════════════════"

# Check Terraform outputs exist
[[ -f "$TF_DIR/outputs.json" ]] || { error "No hay outputs.json"; exit 1; }

RDS_ENDPOINT=$(jq -r '.rds_endpoint.value' "$TF_DIR/outputs.json")
RDS_PORT=$(jq -r '.rds_port.value' "$TF_DIR/outputs.json")
DB_NAME=$(jq -r '.db_name.value' "$TF_DIR/outputs.json")
ACM_CERT_ARN=$(jq -r '.acm_certificate_arn.value' "$TF_DIR/outputs.json")
R53_ZONE_ID=$(jq -r '.route53_zone_id.value' "$TF_DIR/outputs.json")

log "Verificando RDS: $RDS_ENDPOINT:$RDS_PORT"

# 1. RDS accessible
log "1/8 Verificando conectividad RDS..."
wait_for "RDS endpoint resolvible" "dig +short $RDS_ENDPOINT | grep -q ." 60 5

# 2. RDS status available
log "2/8 Verificando estado RDS..."
RDS_STATUS=$(aws rds describe-db-instances --db-instance-identifier aircargo-production --query 'DBInstances[0].DBInstanceStatus' --output text 2>/dev/null)
[[ "$RDS_STATUS" == "available" ]] || { error "RDS no está disponible: $RDS_STATUS"; exit 1; }
log "  RDS Status: $RDS_STATUS"

# 3. RDS Multi-AZ
log "3/8 Verificando Multi-AZ..."
MULTI_AZ=$(aws rds describe-db-instances --db-instance-identifier aircargo-production --query 'DBInstances[0].MultiAZ' --output text)
[[ "$MULTI_AZ" == "True" ]] || { error "RDS no es Multi-AZ"; exit 1; }
log "  Multi-AZ: Habilitado"

# 4. RDS Encrypted
log "4/8 Verificando encriptación..."
ENCRYPTED=$(aws rds describe-db-instances --db-instance-identifier aircargo-production --query 'DBInstances[0].StorageEncrypted' --output text)
[[ "$ENCRYPTED" == "True" ]] || { error "RDS no está encriptado"; exit 1; }
log "  Encriptación: Habilitada"

# 5. Backup retention
log "5/8 Verificando backups..."
BACKUP_RETENTION=$(aws rds describe-db-instances --db-instance-identifier aircargo-production --query 'DBInstances[0].BackupRetentionPeriod' --output text)
[[ "$BACKUP_RETENTION" -ge 30 ]] || { error "Backup retention < 30 días: $BACKUP_RETENTION"; exit 1; }
log "  Backup Retention: ${BACKUP_RETENTION} días"

# 6. ECR Repositories
log "6/8 Verificando repositorios ECR..."
REPOS=(
  "aircargo-auth-service"
  "aircargo-flight-service"
  "aircargo-booking-service"
  "aircargo-mawb-service"
  "aircargo-warehouse-service"
  "aircargo-uld-service"
  "aircargo-load-planning-service"
  "aircargo-export-service"
  "aircargo-notification-service"
  "aircargo-gateway"
  "aircargo-frontend"
)

for repo in "${REPOS[@]}"; do
  aws ecr describe-repositories --repository-names "$repo" >/dev/null 2>&1 || { error "ECR repo faltante: $repo"; exit 1; }
done
log "  11 repositorios ECR verificados"

# 7. ACM Certificate
log "7/8 Verificando certificado ACM..."
CERT_STATUS=$(aws acm describe-certificate --certificate-arn "$ACM_CERT_ARN" --query 'Certificate.Status' --output text 2>/dev/null)
[[ "$CERT_STATUS" == "ISSUED" ]] || { error "Certificado ACM no emitido: $CERT_STATUS"; exit 1; }
log "  Certificado: $CERT_STATUS"

# 8. Route53 Zone
log "8/8 Verificando zona Route53..."
aws route53 get-hosted-zone --id "$R53_ZONE_ID" >/dev/null 2>&1 || { error "Zona Route53 no accesible"; exit 1; }
log "  Zona Route53: OK"

# 9. RabbitMQ in cluster (if EKS is ready)
log "9/9 Verificando RabbitMQ en cluster..."
if kubectl get namespace aircargo >/dev/null 2>&1; then
  wait_for "RabbitMQ pods ready" "kubectl get pods -n aircargo -l app=rabbitmq --field-selector=status.phase=Running | grep -q '3/3'" 300 10
  log "  RabbitMQ: 3 réplicas corriendo"
else
  warn "Cluster EKS no accesible, saltando verificación RabbitMQ"
fi

# 10. K8s secret exists
log "10/10 Verificando secret k8s..."
kubectl get secret aircargo-secrets -n aircargo >/dev/null 2>&1 || { error "Secret aircargo-secrets no existe"; exit 1; }
log "  Secret k8s: OK"

log "══════════════════════════════════════════════════════════════"
log "  VALIDACIÓN PASO 1: EXITOSA"
log "══════════════════════════════════════════════════════════════"
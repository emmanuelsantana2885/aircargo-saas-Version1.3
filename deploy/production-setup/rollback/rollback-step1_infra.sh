#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Step 1 Rollback: Restore DB from Snapshot + Destroy Infrastructure
# ────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/db-snapshot-manager.sh"

TF_DIR="$SCRIPT_DIR/../step1-infra/terraform"

log "══════════════════════════════════════════════════════════════"
log "  ROLLBACK PASO 1: RESTAURAR DB + DESTRUIR INFRAESTRUCTURA"
log "══════════════════════════════════════════════════════════════"

warn "Esto:"
warn "  1. Restaurará la DB desde el último snapshot pre-deploy"
warn "  2. DESTRUIRÁ: RDS, ECR, ACM, Route53, RabbitMQ"
echo ""
read -p "¿CONFIRMAS RESTAURACIÓN + DESTRUCCIÓN? Escribe 'RESTAURAR-Y-DESTRUIR' para confirmar: " confirm
[[ "$confirm" == "RESTAURAR-Y-DESTRUIR" ]] || { error "Cancelado"; exit 1; }

# 1. Restore DB from latest pre-deploy snapshot
log "Paso 1/2: Restaurando base de datos desde snapshot pre-deploy..."
if has_cmd aws && aws rds describe-db-instances --db-instance-identifier aircargo-production --region us-east-1 >/dev/null 2>&1; then
  latest_snap=$(get_latest_snapshot)
  if [[ -n "$latest_snap" && "$latest_snap" != "None" ]]; then
    log "Restaurando desde: $latest_snap"
    restore_rds_snapshot "$latest_snap"
  else
    warn "No hay snapshots pre-deploy disponibles"
  fi
else
  # Local PostgreSQL
  latest_dump=$(get_latest_snapshot)
  if [[ -n "$latest_dump" && -f "$latest_dump" ]]; then
    log "Restaurando dump local: $latest_dump"
    restore_local_pg_dump "$latest_dump"
  else
    warn "No hay dumps locales disponibles"
  fi
fi

# 2. Remove k8s secret
log "Paso 2/2: Limpiando recursos k8s..."
kubectl delete secret aircargo-secrets -n aircargo --ignore-not-found=true

# Remove RabbitMQ Helm release
helm uninstall rabbitmq -n aircargo --wait --timeout 300s 2>/dev/null || true

# Terraform destroy
log "Destruyendo infraestructura con Terraform..."
cd "$TF_DIR"
terraform destroy -auto-approve 2>&1 | tee -a "$SCRIPT_DIR/../../logs/rollback-step1.log"

log "══════════════════════════════════════════════════════════════"
log "  ROLLBACK PASO 1 COMPLETADO (DB restaurada + infra destruida)"
log "══════════════════════════════════════════════════════════════"
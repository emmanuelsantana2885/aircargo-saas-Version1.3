#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Step 3 Rollback: Remove External Secrets + NetworkPolicies
# ────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

log "══════════════════════════════════════════════════════════════"
log "  ROLLBACK PASO 3: ELIMINAR EXTERNAL SECRETS + NETWORKPOLICIES"
log "══════════════════════════════════════════════════════════════"

warn "Esto eliminará:"
warn "  - External Secrets Operator"
warn "  - ClusterSecretStore"
warn "  - ExternalSecret"
warn "  - Secret generado aircargo-secrets"
warn "  - NetworkPolicies (NO elimina las del paso 1, solo las de step3)"
echo ""
read -p "¿CONFIRMAS ELIMINACIÓN? Escribe 'ELIMINAR' para confirmar: " confirm
[[ "$confirm" == "ELIMINAR" ]] || { error "Cancelado"; exit 1; }

# Remove ExternalSecret
log "Eliminando ExternalSecret..."
kubectl delete externalsecret aircargo-secrets -n aircargo --ignore-not-found=true

# Remove generated secret
log "Eliminando secret aircargo-secrets..."
kubectl delete secret aircargo-secrets -n aircargo --ignore-not-found=true

# Remove ClusterSecretStore
log "Eliminando ClusterSecretStore..."
kubectl delete clustersecretstore aws-secretsmanager --ignore-not-found=true

# Uninstall External Secrets Operator
log "Desinstalando External Secrets Operator..."
helm uninstall external-secrets -n external-secrets-system --wait --timeout 180s 2>/dev/null || true

# Remove namespace
kubectl delete namespace external-secrets-system --wait --timeout=120s 2>/dev/null || true

# Remove NetworkPolicies created in step3 (if any specific ones)
# Note: Base NetworkPolicies from step1 are in deploy/k8s/base/09-networkpolicies.yml
# and should NOT be removed here.

log "══════════════════════════════════════════════════════════════"
log "  ROLLBACK PASO 3 COMPLETADO"
log "══════════════════════════════════════════════════════════════"
#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Aircargo Production Setup — Master Orchestrator
# Executes 3 critical steps with approval gates and rollback on failure
# ────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
LOG_DIR="$SCRIPT_DIR/logs"
STATE_FILE="$LOG_DIR/state.json"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

mkdir -p "$LOG_DIR"

log() { echo -e "${GREEN}[SETUP]${NC} $*" | tee -a "$LOG_DIR/master.log"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*" | tee -a "$LOG_DIR/master.log"; }
error() { echo -e "${RED}[ERROR]${NC} $*" | tee -a "$LOG_DIR/master.log"; }
info() { echo -e "${BLUE}[INFO]${NC} $*" | tee -a "$LOG_DIR/master.log"; }

# State management
init_state() {
  cat > "$STATE_FILE" <<EOF
{
  "step1_infra": "pending",
  "step2_observability": "pending",
  "step3_secrets_netpol": "pending",
  "started_at": "$(date -Iseconds)",
  "completed_steps": []
}
EOF
}

update_state() {
  local step="$1"
  local status="$2"
  local tmp=$(mktemp)
  jq --arg step "$step" --arg status "$status" --arg ts "$(date -Iseconds)" '
    .[$step] = $status |
    .last_update = $ts |
    if $status == "completed" then .completed_steps += [$step] else . end
  ' "$STATE_FILE" > "$tmp" && mv "$tmp" "$STATE_FILE"
}

get_state() {
  jq -r ".$1" "$STATE_FILE" 2>/dev/null || echo "unknown"
}

# Approval gate
require_approval() {
  local step_name="$1"
  local description="$2"
  
  echo ""
  echo -e "${BLUE}══════════════════════════════════════════════════════════════${NC}"
  echo -e "${BLUE}  APROBACIÓN REQUERIDA: $step_name${NC}"
  echo -e "${BLUE}══════════════════════════════════════════════════════════════${NC}"
  echo -e "${YELLOW}$description${NC}"
  echo ""
  echo "Esta acción:"
  echo "  • Creará/modificará recursos en tu cuenta cloud (COSTOS REALES)"
  echo "  • Puede tomar 10-30 minutos"
  echo "  • Es irreversible sin intervención manual"
  echo ""
  read -p "¿Continuar? Escribe 'SI' en mayúsculas para confirmar: " confirm
  [[ "$confirm" == "SI" ]] || { error "Cancelado por usuario"; exit 1; }
  log "Aprobación concedida para: $step_name"
}

# Rollback handler
rollback_step() {
  local step="$1"
  log "Iniciando rollback para: $step"
  if [[ -f "$SCRIPT_DIR/rollback/rollback-$step.sh" ]]; then
    bash "$SCRIPT_DIR/rollback/rollback-$step.sh" 2>&1 | tee -a "$LOG_DIR/rollback-$step.log"
    log "Rollback completado para: $step"
  else
    warn "No hay script de rollback para: $step"
  fi
}

# Execute step with validation and rollback
execute_step() {
  local step_id="$1"
  local step_name="$2"
  local script="$3"
  local description="$4"
  local validate_script="$5"
  
  local current_state=$(get_state "$step_id")
  if [[ "$current_state" == "completed" ]]; then
    log "Paso $step_name ya completado, saltando..."
    return 0
  fi
  
  require_approval "$step_name" "$description"
  
  update_state "$step_id" "in_progress"
  log "=== INICIANDO: $step_name ==="
  
  local start_time=$(date +%s)
  if bash "$script" 2>&1 | tee -a "$LOG_DIR/$step_id.log"; then
    log "Ejecución exitosa, validando..."
    if bash "$validate_script" 2>&1 | tee -a "$LOG_DIR/validate-$step_id.log"; then
      update_state "$step_id" "completed"
      local elapsed=$(($(date +%s) - start_time))
      log "=== COMPLETADO: $step_name (${elapsed}s) ==="
      return 0
    else
      error "Validación falló para: $step_name"
      update_state "$step_id" "validation_failed"
      rollback_step "$step_id"
      return 1
    fi
  else
    error "Ejecución falló para: $step_name"
    update_state "$step_id" "failed"
    rollback_step "$step_id"
    return 1
  fi
}

# Main
main() {
  log "══════════════════════════════════════════════════════════════"
  log "  AIRCARGO PRODUCTION SETUP — MASTER ORCHESTRATOR"
  log "══════════════════════════════════════════════════════════════"
  
  # Prerequisites check
  log "Verificando prerequisitos..."
  command -v aws >/dev/null || { error "AWS CLI no instalado"; exit 1; }
  command -v kubectl >/dev/null || { error "kubectl no instalado"; exit 1; }
  command -v helm >/dev/null || { error "Helm no instalado"; exit 1; }
  command -v jq >/dev/null || { error "jq no instalado"; exit 1; }
  command -v terraform >/dev/null || { error "Terraform no instalado"; exit 1; }
  
  # Check AWS credentials
  aws sts get-caller-identity >/dev/null 2>&1 || { error "AWS credentials no configuradas"; exit 1; }
  
  # Check kubectl context
  local ctx=$(kubectl config current-context)
  log "Contexto kubectl actual: $ctx"
  read -p "¿Es el cluster CORRECTO? (s/N): " ok
  [[ "$ok" =~ ^[Ss]$ ]] || { error "Cluster incorrecto"; exit 1; }
  
  init_state
  
  # Step 1: Infrastructure
  execute_step \
    "step1_infra" \
    "INFRAESTRUCTURA GESTIONADA (RDS PostgreSQL + RabbitMQ HA + ECR + DNS/TLS)" \
    "$SCRIPT_DIR/step1-infra/provision-infra.sh" \
    "Provisiona: RDS Multi-AZ, RabbitMQ Cluster, ECR Repos, Route53, ACM Certs" \
    "$SCRIPT_DIR/step1-infra/validate-infra.sh"
  
  # Step 2: Observability
  execute_step \
    "step2_observability" \
    "STACK OBSERVABILIDAD (Prometheus, Grafana, Loki, Tempo, Alertmanager)" \
    "$SCRIPT_DIR/step2-observability/install-observability.sh" \
    "Instala: kube-prometheus-stack, Loki, Tempo, dashboards, alertas" \
    "$SCRIPT_DIR/step2-observability/validate-observability.sh"
  
  # Step 3: Secrets + NetworkPolicies
  execute_step \
    "step3_secrets_netpol" \
    "EXTERNAL SECRETS + NETWORKPOLICIES + VALIDACIÓN SEGURIDAD" \
    "$SCRIPT_DIR/step3-secrets-netpol/setup-secrets-netpol.sh" \
    "Instala: External Secrets Operator, valida NetworkPolicies, rota secrets" \
    "$SCRIPT_DIR/step3-secrets-netpol/validate-secrets-netpol.sh"
  
  log "══════════════════════════════════════════════════════════════"
  log "  TODOS LOS PASOS CRÍTICOS COMPLETADOS EXITOSAMENTE"
  log "══════════════════════════════════════════════════════════════"
  log "Próximos pasos manuales:"
  log "  1. Ejecutar deploy: ./deploy/deploy.sh production"
  log "  2. Configurar DNS real en Route53"
  log "  3. Validar end-to-end en producción"
}

main "$@"
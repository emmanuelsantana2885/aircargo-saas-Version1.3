#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Shared library for production setup scripts
# ────────────────────────────────────────────────────────────────

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${GREEN}[LIB]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }
info() { echo -e "${BLUE}[INFO]${NC} $*"; }

# Wait for condition with timeout
wait_for() {
  local desc="$1"
  local cmd="$2"
  local timeout="${3:-300}"
  local interval="${4:-10}"
  
  log "Esperando: $desc (timeout: ${timeout}s)"
  local start=$(date +%s)
  while ! eval "$cmd" >/dev/null 2>&1; do
    local elapsed=$(($(date +%s) - start))
    if [[ $elapsed -gt $timeout ]]; then
      error "Timeout esperando: $desc"
      return 1
    fi
    sleep $interval
  done
  log "✓ $desc"
}

# Wait for AWS resource
wait_for_aws() {
  local desc="$1"
  local cmd="$2"
  local timeout="${3:-600}"
  wait_for "$desc" "$cmd" "$timeout" 15
}

# Retry with exponential backoff
retry() {
  local max_attempts="${1:-3}"
  local delay="${2:-5}"
  local cmd="${@:3}"
  local attempt=1
  
  while true; do
    if eval "$cmd"; then
      return 0
    fi
    if [[ $attempt -ge $max_attempts ]]; then
      error "Falló tras $max_attempts intentos: $cmd"
      return 1
    fi
    warn "Intento $attempt falló, reintentando en ${delay}s..."
    sleep $delay
    delay=$((delay * 2))
    ((attempt++))
  done
}

# Check if resource exists
resource_exists() {
  local type="$1"
  local name="$2"
  local namespace="${3:-}"
  
  if [[ -n "$namespace" ]]; then
    kubectl get "$type" "$name" -n "$namespace" >/dev/null 2>&1
  else
    kubectl get "$type" "$name" >/dev/null 2>&1
  fi
}

# Apply k8s manifest with validation
apply_manifest() {
  local file="$1"
  local namespace="${2:-}"
  
  log "Aplicando: $file"
  if [[ -n "$namespace" ]]; then
    kubectl apply -f "$file" -n "$namespace"
  else
    kubectl apply -f "$file"
  fi
}

# Helm install/upgrade with wait
helm_deploy() {
  local release="$1"
  local chart="$2"
  local namespace="$3"
  local values="${4:-}"
  local wait="${5:-true}"
  local timeout="${6:-600s}"
  
  local cmd="helm upgrade --install $release $chart -n $namespace --create-namespace"
  [[ -n "$values" ]] && cmd="$cmd -f $values"
  [[ "$wait" == "true" ]] && cmd="$cmd --wait --timeout $timeout"
  
  log "Desplegando Helm: $release"
  eval "$cmd"
}

# Get Terraform output
tf_output() {
  local key="$1"
  local dir="${2:-}"
  [[ -n "$dir" ]] && cd "$dir"
  terraform output -raw "$key" 2>/dev/null
}

# Generate secure password
gen_password() {
  local len="${1:-32}"
  openssl rand -base64 48 | tr -d '/+=' | cut -c1-"$len"
}

# Export for use in other scripts
export -f log warn error info wait_for wait_for_aws retry resource_exists apply_manifest helm_deploy tf_output gen_password
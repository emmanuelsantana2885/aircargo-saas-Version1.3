#!/usr/bin/env bash
# Production deployment script for Aircargo
# Usage: ./deploy.sh [staging|production] [--dry-run]

set -euo pipefail

ENVIRONMENT="${1:-staging}"
DRY_RUN="${2:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
KUSTOMIZE_DIR="$ROOT_DIR/deploy/k8s/overlays/$ENVIRONMENT"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${GREEN}[DEPLOY]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(staging|production)$ ]]; then
  error "Environment must be 'staging' or 'production'"
fi

if [[ ! -d "$KUSTOMIZE_DIR" ]]; then
  error "Kustomize directory not found: $KUSTOMIZE_DIR"
fi

# Check prerequisites
command -v kubectl >/dev/null 2>&1 || error "kubectl not found"
command -v kustomize >/dev/null 2>&1 || error "kustomize not found"

# Check kubectl context
CURRENT_CONTEXT=$(kubectl config current-context)
log "Current kubectl context: $CURRENT_CONTEXT"

if [[ "$ENVIRONMENT" == "production" && "$CURRENT_CONTEXT" != *"prod"* ]]; then
  warn "Deploying to PRODUCTION but context doesn't contain 'prod'"
  read -p "Are you sure? (yes/no): " confirm
  [[ "$confirm" == "yes" ]] || error "Aborted"
fi

# Check for required secrets
log "Checking for required secrets..."
if ! kubectl get secret aircargo-secrets -n aircargo >/dev/null 2>&1; then
  warn "Secret 'aircargo-secrets' not found in namespace 'aircargo'"
  warn "Create it from deploy/k8s/base/02-secrets.yml.template"
  if [[ -z "$DRY_RUN" ]]; then
    read -p "Continue anyway? (yes/no): " confirm
    [[ "$confirm" == "yes" ]] || error "Aborted"
  fi
fi

# Build kustomize
log "Building kustomize for $ENVIRONMENT..."
cd "$KUSTOMIZE_DIR"
MANIFEST=$(kustomize build .)

if [[ -n "$DRY_RUN" ]]; then
  log "=== DRY RUN - Would apply: ==="
  echo "$MANIFEST" | head -200
  echo "..."
  exit 0
fi

# Apply
log "Applying manifests to $ENVIRONMENT..."
echo "$MANIFEST" | kubectl apply -f -

# Wait for deployments
log "Waiting for deployments to be ready..."
kubectl wait --for=condition=available deployment --all -n aircargo --timeout=300s

# Verify
log "Verifying deployment..."
kubectl get pods -n aircargo -o wide

# Health checks
log "Running health checks..."
GATEWAY_URL=$(kubectl get ingress aircargo-gateway-ingress -n aircargo -o jsonpath='{.spec.rules[0].host}')
if [[ -n "$GATEWAY_URL" ]]; then
  if curl -sf "https://$GATEWAY_URL/actuator/health" | grep -q '"status":"UP"'; then
    log "✅ Gateway health check passed"
  else
    warn "⚠️  Gateway health check failed"
  fi
fi

FRONTEND_URL=$(kubectl get ingress aircargo-frontend-ingress -n aircargo -o jsonpath='{.spec.rules[0].host}')
if [[ -n "$FRONTEND_URL" ]]; then
  if curl -sf "https://$FRONTEND_URL/" >/dev/null; then
    log "✅ Frontend health check passed"
  else
    warn "⚠️  Frontend health check failed"
  fi
fi

log "🎉 Deployment to $ENVIRONMENT complete!"
log "Gateway: https://$GATEWAY_URL"
log "Frontend: https://$FRONTEND_URL"
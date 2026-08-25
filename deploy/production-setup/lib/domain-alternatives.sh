#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Domain Alternatives for No Real Domain
# Options: nip.io, sslip.io, local /etc/hosts, kind/k3d ingress
# ────────────────────────────────────────────────────────────────

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${GREEN}[DOMAIN]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }
info() { echo -e "${BLUE}[INFO]${NC} $*"; }

# Get local IP
get_local_ip() {
  ip route get 1.1.1.1 2>/dev/null | awk '{print $7; exit}' || hostname -I | awk '{print $1}'
}

LOCAL_IP=$(get_local_ip)
NIP_DOMAIN="${LOCAL_IP}.nip.io"
SSlip_DOMAIN="${LOCAL_IP}.sslip.io"

show_options() {
  log "══════════════════════════════════════════════════════════════"
  log "  OPCIONES DE DOMINIO SIN DOMINIO REAL"
  log "══════════════════════════════════════════════════════════════"
  log ""
  log "Tu IP local: $LOCAL_IP"
  log ""
  log "OPCIÓN 1: nip.io (RECOMENDADA - automática, gratis, wildcard)"
  log "  Frontend:  https://aircargo.${NIP_DOMAIN}"
  log "  API:       https://api.${NIP_DOMAIN}"
  log "  Ventajas:  No config, wildcard automático, HTTPS con cert-manager"
  log ""
  log "OPCIÓN 2: sslip.io (similar a nip.io)"
  log "  Frontend:  https://aircargo.${SSlip_DOMAIN}"
  log "  API:       https://api.${SSlip_DOMAIN}"
  log ""
  log "OPCIÓN 3: /etc/hosts local (solo tu máquina)"
  log "  Agregar a /etc/hosts:"
  log "  $LOCAL_IP aircargo.local api.aircargo.local"
  log "  Usar: aircargo.local / api.aircargo.local"
  log ""
  log "OPCIÓN 4: kind/k3d con ingress local (desarrollo puro local)"
  log "  ./setup-local-cluster.sh  # Crea cluster local con ingress"
  log "  Usa: aircargo.localhost / api.aircargo.localhost"
  log ""
  log "OPCIÓN 5: Dominio real barato (Namecheap, Cloudflare, Porkbun ~$10/año)"
  log "  Comprar + delegar NS a Cloudflare (gratis) + cert-manager auto"
}

generate_configs() {
  local option="$1"
  
  case "$option" in
    1|nip)
      cat > /tmp/aircargo-domain.env <<EOF
# nip.io configuration
export DOMAIN_TYPE="nip.io"
export FRONTEND_DOMAIN="aircargo.${NIP_DOMAIN}"
export API_DOMAIN="api.${NIP_DOMAIN}"
export ACME_EMAIL="admin@${NIP_DOMAIN}"
export CORS_ORIGINS="https://aircargo.${NIP_DOMAIN}"
EOF
      log "Configuración generada en /tmp/aircargo-domain.env"
      ;;
    2|sslip)
      cat > /tmp/aircargo-domain.env <<EOF
# sslip.io configuration
export DOMAIN_TYPE="sslip.io"
export FRONTEND_DOMAIN="aircargo.${SSlip_DOMAIN}"
export API_DOMAIN="api.${SSlip_DOMAIN}"
export ACME_EMAIL="admin@${SSlip_DOMAIN}"
export CORS_ORIGINS="https://aircargo.${SSlip_DOMAIN}"
EOF
      log "Configuración generada en /tmp/aircargo-domain.env"
      ;;
    3|hosts)
      cat > /tmp/aircargo-domain.env <<EOF
# /etc/hosts configuration
export DOMAIN_TYPE="hosts"
export FRONTEND_DOMAIN="aircargo.local"
export API_DOMAIN="api.aircargo.local"
export ACME_EMAIL="admin@aircargo.local"
export CORS_ORIGINS="https://aircargo.local"
EOF
      log "Configuración generada en /tmp/aircargo-domain.env"
      log "EJECUTA: echo \"$LOCAL_IP aircargo.local api.aircargo.local\" | sudo tee -a /etc/hosts"
      ;;
    4|local)
      cat > /tmp/aircargo-domain.env <<EOF
# Local cluster (kind/k3d) configuration
export DOMAIN_TYPE="local"
export FRONTEND_DOMAIN="aircargo.localhost"
export API_DOMAIN="api.aircargo.localhost"
export ACME_EMAIL="admin@aircargo.localhost"
export CORS_ORIGINS="https://aircargo.localhost"
EOF
      log "Configuración generada en /tmp/aircargo-domain.env"
      log "Ejecuta: ./setup-local-cluster.sh"
      ;;
    *)
      error "Opción inválida: $option"
      exit 1
      ;;
  esac
}

apply_to_kustomize() {
  local domain_file="/tmp/aircargo-domain.env"
  [[ -f "$domain_file" ]] || { error "Ejecuta primero: $0 generate <opción>"; exit 1; }
  
  source "$domain_file"
  
  log "Actualizando kustomization.yml para staging y production..."
  
  for env in staging production; do
    local kustomize_file="/home/manolov/Desktop/Projects/Rannik/aircargo-saas-Version1.2/deploy/k8s/overlays/$env/kustomization.yml"
    [[ -f "$kustomize_file" ]] || { warn "No existe: $kustomize_file"; continue; }
    
    # Backup
    cp "$kustomize_file" "${kustomize_file}.bak"
    
    # Update patchesJson6902 for ingress hosts
    sed -i "s|staging-api.aircargo.example.com|${API_DOMAIN}|g" "$kustomize_file"
    sed -i "s|staging.aircargo.example.com|${FRONTEND_DOMAIN}|g" "$kustomize_file"
    sed -i "s|api.aircargo.example.com|${API_DOMAIN}|g" "$kustomize_file"
    sed -i "s|aircargo.example.com|${FRONTEND_DOMAIN}|g" "$kustomize_file"
    
    # Update configMapGenerator
    sed -i "s|FRONTEND_URL=.*|FRONTEND_URL=https://${FRONTEND_DOMAIN}|g" "$kustomize_file"
    sed -i "s|CORS_ORIGINS=.*|CORS_ORIGINS=https://${FRONTEND_DOMAIN}|g" "$kustomize_file"
    
    log "Actualizado: $kustomize_file"
  done
  
  log "Configuración aplicada. Revisa los archivos antes de deploy."
}

setup_local_cluster() {
  log "Configurando cluster local con kind + ingress-nginx..."
  
  # Create kind config with ingress
  cat > /tmp/kind-config.yaml <<'EOF'
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
EOF
  
  log "Creando cluster kind..."
  kind create cluster --name aircargo-local --config /tmp/kind-config.yaml
  
  log "Instalando ingress-nginx..."
  kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
  
  log "Esperando ingress-nginx..."
  kubectl wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=120s
  
  log "Cluster local listo en: https://aircargo.localhost / https://api.aircargo.localhost"
  log "Agrega a /etc/hosts: 127.0.0.1 aircargo.localhost api.aircargo.localhost"
}

main() {
  local cmd="${1:-show}"
  
  case "$cmd" in
    show)
      show_options
      ;;
    generate)
      generate_configs "${2:-1}"
      ;;
    apply)
      apply_to_kustomize
      ;;
    local)
      setup_local_cluster
      ;;
    *)
      show_options
      echo ""
      log "Uso: $0 [show|generate <1-4>|apply|local]"
      log "  show      - Mostrar opciones"
      log "  generate  - Generar config (1=nip.io, 2=sslip.io, 3=/etc/hosts, 4=local)"
      log "  apply     - Aplicar a kustomization.yml"
      log "  local     - Crear cluster kind local con ingress"
      ;;
  esac
}

main "$@"
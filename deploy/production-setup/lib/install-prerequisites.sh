#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Install All Prerequisites for Production Setup
# Run with: sudo ./install-prerequisites.sh
# ────────────────────────────────────────────────────────────────

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${GREEN}[INSTALL]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }
info() { echo -e "${BLUE}[INFO]${NC} $*"; }

# Detect OS
detect_os() {
  if [[ -f /etc/os-release ]]; then
    . /etc/os-release
    OS=$ID
    VER=$VERSION_ID
  elif [[ -f /etc/arch-release ]]; then
    OS="arch"
  elif [[ "$OSTYPE" == "darwin"* ]]; then
    OS="macos"
  else
    error "OS no soportado"
    exit 1
  fi
  log "OS detectado: $OS $VER"
}

# Check if command exists
has_cmd() { command -v "$1" >/dev/null 2>&1; }

# Install on Ubuntu/Debian
install_debian() {
  log "Actualizando repositorios..."
  apt-get update -y
  
  log "Instalando dependencias base..."
  apt-get install -y curl wget gnupg2 software-properties-common apt-transport-https ca-certificates lsb-release unzip jq
  
  # AWS CLI v2
  if ! has_cmd aws; then
    log "Instalando AWS CLI v2..."
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/tmp/awscliv2.zip"
    unzip -q /tmp/awscliv2.zip -d /tmp
    /tmp/aws/install
    rm -rf /tmp/awscliv2.zip /tmp/aws
  fi
  
  # kubectl
  if ! has_cmd kubectl; then
    log "Instalando kubectl..."
    curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
    install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
    rm kubectl
  fi
  
  # Helm
  if ! has_cmd helm; then
    log "Instalando Helm..."
    curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
  fi
  
  # Terraform
  if ! has_cmd terraform; then
    log "Instalando Terraform..."
    TERRAFORM_VERSION="1.8.5"
    wget -q "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip" -O /tmp/terraform.zip
    unzip -q /tmp/terraform.zip -d /usr/local/bin/
    rm /tmp/terraform.zip
  fi
  
  # Docker (for local testing)
  if ! has_cmd docker; then
    log "Instalando Docker..."
    curl -fsSL https://get.docker.com | bash
    usermod -aG docker "$SUDO_USER" 2>/dev/null || true
  fi
  
  # kind (for local k8s)
  if ! has_cmd kind; then
    log "Instalando kind..."
    curl -Lo /usr/local/bin/kind "https://kind.sigs.k8s.io/dl/v0.23.0/kind-linux-amd64"
    chmod +x /usr/local/bin/kind
  fi
  
  # k3d (lighter alternative)
  if ! has_cmd k3d; then
    log "Instalando k3d..."
    curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
  fi
}

# Install on Arch Linux
install_arch() {
  log "Instalando en Arch Linux..."
  pacman -Sy --needed --noconfirm \
    aws-cli-v2 \
    kubectl \
    helm \
    terraform \
    docker \
    kind \
    k3d \
    jq \
    unzip \
    curl \
    wget
  
  # Enable Docker
  systemctl enable --now docker
  usermod -aG docker "$SUDO_USER" 2>/dev/null || true
}

# Install on Fedora/RHEL
install_fedora() {
  log "Instalando en Fedora/RHEL..."
  dnf install -y curl wget gnupg2 jq unzip
  
  # AWS CLI
  if ! has_cmd aws; then
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/tmp/awscliv2.zip"
    unzip -q /tmp/awscliv2.zip -d /tmp
    /tmp/aws/install
    rm -rf /tmp/awscliv2.zip /tmp/aws
  fi
  
  # kubectl
  if ! has_cmd kubectl; then
    curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
    install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
    rm kubectl
  fi
  
  # Helm
  if ! has_cmd helm; then
    curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
  fi
  
  # Terraform
  if ! has_cmd terraform; then
    dnf config-manager --add-repo https://rpm.releases.hashicorp.com/fedora/hashicorp.repo
    dnf install -y terraform
  fi
  
  # Docker + kind + k3d
  dnf install -y docker
  systemctl enable --now docker
  usermod -aG docker "$SUDO_USER" 2>/dev/null || true
  
  curl -Lo /usr/local/bin/kind "https://kind.sigs.k8s.io/dl/v0.23.0/kind-linux-amd64"
  chmod +x /usr/local/bin/kind
  curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
}

# Install on macOS
install_macos() {
  log "Instalando en macOS (requiere Homebrew)..."
  if ! has_cmd brew; then
    error "Homebrew no instalado. Instala desde https://brew.sh"
    exit 1
  fi
  
  brew install awscli kubectl helm terraform docker kind k3d jq
  
  # Start Docker Desktop (manual)
  warn "Inicia Docker Desktop manualmente después de la instalación"
}

# Verify installations
verify() {
  log "Verificando instalaciones..."
  local tools=("aws" "kubectl" "helm" "terraform" "docker" "kind" "k3d" "jq")
  local failed=0
  
  for tool in "${tools[@]}"; do
    if has_cmd "$tool"; then
      local version=$($tool version 2>/dev/null | head -1 || $tool --version 2>/dev/null | head -1)
      log "  ✓ $tool: $version"
    else
      warn "  ✗ $tool: NO ENCONTRADO"
      ((failed++))
    fi
  done
  
  if [[ $failed -gt 0 ]]; then
    error "$failed herramientas no instaladas correctamente"
    return 1
  fi
  log "Todas las herramientas instaladas correctamente"
}

# Main
main() {
  log "══════════════════════════════════════════════════════════════"
  log "  INSTALADOR DE PRERREQUISITOS PARA AIRCARGO PRODUCTION"
  log "══════════════════════════════════════════════════════════════"
  
  if [[ $EUID -ne 0 ]]; then
    error "Este script debe ejecutarse con sudo"
    exit 1
  fi
  
  detect_os
  
  case "$OS" in
    ubuntu|debian|linuxmint|pop)
      install_debian
      ;;
    arch|manjaro|endeavouros)
      install_arch
      ;;
    fedora|rhel|centos|rocky|almalinux)
      install_fedora
      ;;
    macos)
      install_macos
      ;;
    *)
      error "OS no soportado: $OS"
      exit 1
      ;;
  esac
  
  verify
  
  log "══════════════════════════════════════════════════════════════"
  log "  INSTALACIÓN COMPLETADA"
  log "══════════════════════════════════════════════════════════════"
  log ""
  log "IMPORTANTE:"
  log "  1. Reinicia tu terminal o ejecuta: newgrp docker"
  log "  2. Configura AWS: aws configure"
  log "  3. Para kind/k3d: no necesitas cluster cloud aún"
}

main "$@"
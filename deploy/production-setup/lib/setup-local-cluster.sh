#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Local Development Setup (kind/k3d) - No Cloud Required
# Creates complete local environment for testing
# ────────────────────────────────────────────────────────────────

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${GREEN}[LOCAL-SETUP]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }
info() { echo -e "${BLUE}[INFO]${NC} $*"; }

CLUSTER_NAME="aircargo-local"
NAMESPACE="aircargo"

check_prereqs() {
  log "Verificando prerequisitos..."
  command -v docker >/dev/null || { error "Docker no instalado"; exit 1; }
  command -v kind >/dev/null || { error "kind no instalado"; exit 1; }
  command -v kubectl >/dev/null || { error "kubectl no instalado"; exit 1; }
  command -v helm >/dev/null || { error "Helm no instalado"; exit 1; }
  log "Prerequisitos OK"
}

create_kind_cluster() {
  log "Creando cluster kind con ingress..."
  
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
      - containerPort: 30080
        hostPort: 30080
        protocol: TCP
      - containerPort: 30443
        hostPort: 30443
        protocol: TCP
EOF
  
  kind create cluster --name "$CLUSTER_NAME" --config /tmp/kind-config.yaml
  
  log "Cluster creado: $CLUSTER_NAME"
}

install_ingress() {
  log "Instalando ingress-nginx para kind..."
  
  kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
  
  log "Esperando ingress-nginx..."
  kubectl wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=120s
  
  log "Ingress listo en puertos 80/443"
}

install_local_postgres() {
  log "Instalando PostgreSQL local (Bitnami Helm)..."
  
  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo update
  
  cat > /tmp/postgres-values.yaml <<'EOF'
auth:
  username: aircargo_user
  password: aircargo_pass_2024
  database: aircargo_db
primary:
  persistence:
    enabled: true
    size: 10Gi
  resources:
    requests:
      memory: 512Mi
      cpu: 250m
    limits:
      memory: 1Gi
      cpu: 1000m
  service:
    type: ClusterIP
    port: 5432
EOF
  
  helm upgrade --install postgres bitnami/postgresql \
    -n "$NAMESPACE" --create-namespace \
    -f /tmp/postgres-values.yaml \
    --wait --timeout 300s
  
  log "PostgreSQL listo en postgres.$NAMESPACE.svc.cluster.local:5432"
}

install_local_rabbitmq() {
  log "Instalando RabbitMQ local..."
  
  cat > /tmp/rabbitmq-values.yaml <<'EOF'
auth:
  username: aircargo
  password: aircargo_pass_2024
replicaCount: 1
clustering:
  enabled: false
persistence:
  enabled: true
  size: 5Gi
resources:
  requests:
    memory: 512Mi
    cpu: 250m
  limits:
    memory: 1Gi
    cpu: 1000m
service:
  type: ClusterIP
EOF
  
  helm upgrade --install rabbitmq bitnami/rabbitmq \
    -n "$NAMESPACE" \
    -f /tmp/rabbitmq-values.yaml \
    --wait --timeout 300s
  
  log "RabbitMQ listo en rabbitmq.$NAMESPACE.svc.cluster.local:5672"
}

create_secrets() {
  log "Creando secrets locales..."
  
  kubectl create secret generic aircargo-secrets -n "$NAMESPACE" \
    --from-literal=JWT_SECRET="$(openssl rand -base64 64)" \
    --from-literal=POSTGRES_HOST="postgres.$NAMESPACE.svc.cluster.local" \
    --from-literal=POSTGRES_PORT="5432" \
    --from-literal=POSTGRES_DB="aircargo_db" \
    --from-literal=POSTGRES_USER="aircargo_user" \
    --from-literal=POSTGRES_PASSWORD="aircargo_pass_2024" \
    --from-literal=RABBITMQ_HOST="rabbitmq.$NAMESPACE.svc.cluster.local" \
    --from-literal=RABBITMQ_PORT="5672" \
    --from-literal=RABBITMQ_USER="aircargo" \
    --from-literal=RABBITMQ_PASSWORD="aircargo_pass_2024" \
    --from-literal=APP_ENCRYPTION_KEY="$(openssl rand -base64 32)" \
    --from-literal=SMTP_HOST="smtp.example.com" \
    --from-literal=SMTP_PORT="587" \
    --from-literal=SMTP_USERNAME="noreply@example.com" \
    --from-literal=SMTP_PASSWORD="changeme" \
    --from-literal=SMTP_FROM="noreply@example.com" \
    --from-literal=SMTP_AUTH="true" \
    --from-literal=SMTP_STARTTLS="true" \
    --from-literal=CORS_ORIGINS="https://aircargo.localhost,https://api.aircargo.localhost" \
    --from-literal=FRONTEND_URL="https://aircargo.localhost" \
    --from-literal=COOKIE_SECURE="false" \
    --dry-run=client -o yaml | kubectl apply -f -
  
  log "Secrets creados"
}

create_configmap() {
  log "Creando ConfigMap..."
  
  cat > /tmp/configmap.yaml <<'EOF'
apiVersion: v1
kind: ConfigMap
metadata:
  name: aircargo-config
  namespace: aircargo
data:
  POSTGRES_HOST: "postgres.aircargo.svc.cluster.local"
  POSTGRES_PORT: "5432"
  POSTGRES_DB: "aircargo_db"
  RABBITMQ_HOST: "rabbitmq.aircargo.svc.cluster.local"
  RABBITMQ_PORT: "5672"
  FRONTEND_URL: "https://aircargo.localhost"
  CORS_ORIGINS: "https://aircargo.localhost"
  COOKIE_SECURE: "false"
  SMTP_AUTH: "true"
  SMTP_STARTTLS: "true"
  SPRING_PROFILES_ACTIVE: "production"
  LOG_LEVEL_ROOT: "INFO"
  LOG_LEVEL_COM_AIRCARGO: "DEBUG"
EOF
  
  kubectl apply -f /tmp/configmap.yaml
  log "ConfigMap creado"
}

setup_local_dns() {
  log "Configurando DNS local (/etc/hosts)..."
  
  local entries="127.0.0.1 aircargo.localhost api.aircargo.localhost"
  
  if grep -q "aircargo.localhost" /etc/hosts; then
    log "Entradas ya existen en /etc/hosts"
  else
    echo "$entries" | sudo tee -a /etc/hosts >/dev/null
    log "Agregado a /etc/hosts: $entries"
  fi
}

build_and_load_images() {
  log "Construyendo y cargando imágenes en kind..."
  
  local root="/home/manolov/Desktop/Projects/Rannik/aircargo-saas-Version1.2"
  
  # Backend services
  for svc in aircargo-auth-service aircargo-flight-service aircargo-booking-service aircargo-mawb-service aircargo-warehouse-service aircargo-uld-service aircargo-load-planning-service aircargo-export-service aircargo-notification-service aircargo-gateway; do
    log "Building $svc..."
    docker build -t "$svc:local" -f "$root/backend/$svc/Dockerfile" "$root/backend"
    kind load docker-image "$svc:local" --name "$CLUSTER_NAME"
  done
  
  # Frontend
  log "Building frontend..."
  docker build -t aircargo-frontend:local -f "$root/frontend/Dockerfile" "$root"
  kind load docker-image aircargo-frontend:local --name "$CLUSTER_NAME"
  
  log "Imágenes cargadas en kind"
}

deploy_services() {
  log "Desplegando servicios con imágenes locales..."
  
  cd /home/manolov/Desktop/Projects/Rannik/aircargo-saas-Version1.2/deploy/k8s/overlays/staging
  
  # Update images to local
  for svc in aircargo-auth-service aircargo-flight-service aircargo-booking-service aircargo-mawb-service aircargo-warehouse-service aircargo-uld-service aircargo-load-planning-service aircargo-export-service aircargo-notification-service aircargo-gateway aircargo-frontend; do
    kustomize edit set image "ghcr.io/your-org/aircargo-saas-version1-2/$svc=$svc:local"
  done
  
  # Update domains for local
  kustomize edit set image "ghcr.io/your-org/aircargo-saas-version1-2/aircargo-frontend=aircargo-frontend:local"
  
  # Apply
  kubectl apply -k .
  
  log "Servicios desplegados. Esperando rollout..."
  
  for svc in aircargo-auth-service aircargo-flight-service aircargo-booking-service aircargo-mawb-service aircargo-warehouse-service aircargo-uld-service aircargo-load-planning-service aircargo-export-service aircargo-notification-service aircargo-gateway aircargo-frontend; do
    kubectl rollout status deployment/$svc -n "$NAMESPACE" --timeout=300s || true
  done
}

verify_deployment() {
  log "Verificando despliegue..."
  
  kubectl get pods -n "$NAMESPACE"
  
  log ""
  log "URLs de acceso:"
  log "  Frontend:  https://aircargo.localhost"
  log "  API:       https://api.aircargo.localhost"
  log "  Grafana:   kubectl port-forward -n monitoring svc/grafana 3000:3000"
  log ""
  log "Usuarios de prueba:"
  log "  supervisor@aircargo.com (SUPER_USER, sin password)"
  log "  readonly@aircargo.com (READ_ONLY, sin password)"
  log "  admin@aircargo.com (ADMIN, requiere password)"
}

cleanup() {
  log "Limpiando cluster local..."
  kind delete cluster --name "$CLUSTER_NAME"
  sudo sed -i '/aircargo.localhost/d' /etc/hosts
  log "Limpieza completa"
}

main() {
  local cmd="${1:-all}"
  
  case "$cmd" in
    all)
      check_prereqs
      create_kind_cluster
      install_ingress
      install_local_postgres
      install_local_rabbitmq
      create_secrets
      create_configmap
      setup_local_dns
      build_and_load_images
      deploy_services
      verify_deployment
      ;;
    infra)
      check_prereqs
      create_kind_cluster
      install_ingress
      install_local_postgres
      install_local_rabbitmq
      create_secrets
      create_configmap
      setup_local_dns
      ;;
    build)
      build_and_load_images
      ;;
    deploy)
      deploy_services
      ;;
    verify)
      verify_deployment
      ;;
    dns)
      setup_local_dns
      ;;
    cleanup)
      cleanup
      ;;
    *)
      log "Uso: $0 [all|infra|build|deploy|verify|dns|cleanup]"
      log "  all      - Setup completo (infra + build + deploy)"
      log "  infra    - Solo infraestructura (kind, postgres, rabbitmq, ingress)"
      log "  build    - Build y carga de imágenes"
      log "  deploy   - Deploy de servicios"
      log "  verify   - Verificar despliegue"
      log "  dns      - Configurar /etc/hosts"
      log "  cleanup  - Eliminar todo"
      ;;
  esac
}

main "$@"
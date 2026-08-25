#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Step 1: Provision Managed Infrastructure
# RDS PostgreSQL Multi-AZ, RabbitMQ HA (EKS/EC2), ECR Repos, Route53, ACM
# ────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

TF_DIR="$SCRIPT_DIR/terraform"
mkdir -p "$TF_DIR"

log "══════════════════════════════════════════════════════════════"
log "  PASO 1: PROVISIONAR INFRAESTRUCTURA GESTIONADA"
log "══════════════════════════════════════════════════════════════"

# Check if already provisioned
if [[ -f "$TF_DIR/terraform.tfstate" ]] && terraform -chdir="$TF_DIR" state list | grep -q "aws_db_instance"; then
  log "Infraestructura ya provisionada (estado Terraform existe)"
  exit 0
fi

# Generate Terraform configuration
log "Generando configuración Terraform..."

cat > "$TF_DIR/main.tf" <<'TFEOF'
terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.10"
    }
  }
  backend "s3" {
    bucket         = "aircargo-terraform-state"
    key            = "production/infra.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-locks"
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = "aircargo"
      Environment = "production"
      ManagedBy   = "terraform"
    }
  }
}

provider "kubernetes" {
  config_path = "~/.kube/config"
}

provider "helm" {
  kubernetes {
    config_path = "~/.kube/config"
  }
}

# ─── VPC (assume existing or create) ───
data "aws_vpc" "main" {
  filter {
    name   = "tag:Name"
    values = ["aircargo-vpc"]
  }
}

data "aws_subnet_ids" "private" {
  vpc_id = data.aws_vpc.main.id
  filter {
    name   = "tag:Type"
    values = ["private"]
  }
}

data "aws_subnet_ids" "public" {
  vpc_id = data.aws_vpc.main.id
  filter {
    name   = "tag:Type"
    values = ["public"]
  }
}

# ─── RDS PostgreSQL Multi-AZ ───
resource "aws_db_subnet_group" "aircargo" {
  name       = "aircargo-db-subnet-group"
  subnet_ids = data.aws_subnet_ids.private.ids
  tags = { Name = "aircargo-db-subnet-group" }
}

resource "aws_security_group" "rds" {
  name        = "aircargo-rds-sg"
  description = "Security group for RDS PostgreSQL"
  vpc_id      = data.aws_vpc.main.id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_instance" "aircargo" {
  identifier             = "aircargo-production"
  engine                 = "postgres"
  engine_version         = "16.4"
  instance_class         = "db.r6g.xlarge"
  allocated_storage      = 100
  max_allocated_storage  = 500
  storage_encrypted      = true
  storage_type           = "gp3"
  multi_az               = true
  
  db_name                = "aircargo_db"
  username               = "aircargo_user"
  password               = var.db_password
  
  db_subnet_group_name   = aws_db_subnet_group.aircargo.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  
  backup_retention_period = 30
  backup_window           = "03:00-04:00"
  maintenance_window      = "mon:04:00-mon:05:00"
  
  performance_insights_enabled = true
  monitoring_interval         = 60
  monitoring_role_arn         = aws_iam_role.rds_monitoring.arn
  
  deletion_protection = true
  skip_final_snapshot = false
  final_snapshot_identifier = "aircargo-final-snapshot-${formatdate("YYYYMMDD", timestamp())}"
  
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  
  parameter_group_name = aws_db_parameter_group.aircargo.name
  
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_db_parameter_group" "aircargo" {
  family = "postgres16"
  name   = "aircargo-pg16"
  
  parameter {
    name  = "max_connections"
    value = "300"
  }
  parameter {
    name  = "shared_buffers"
    value = "{DBInstanceClassMemory/4}"
  }
  parameter {
    name  = "effective_cache_size"
    value = "{DBInstanceClassMemory*3/4}"
  }
  parameter {
    name  = "work_mem"
    value = "16MB"
  }
  parameter {
    name  = "maintenance_work_mem"
    value = "256MB"
  }
  parameter {
    name  = "checkpoint_completion_target"
    value = "0.9"
  }
  parameter {
    name  = "wal_buffers"
    value = "16MB"
  }
  parameter {
    name  = "default_statistics_target"
    value = "100"
  }
  parameter {
    name  = "random_page_cost"
    value = "1.1"
  }
  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }
  parameter {
    name  = "log_checkpoints"
    value = "on"
  }
  parameter {
    name  = "log_connections"
    value = "on"
  }
  parameter {
    name  = "log_disconnections"
    value = "on"
  }
  parameter {
    name  = "log_lock_waits"
    value = "on"
  }
  parameter {
    name  = "autovacuum"
    value = "on"
  }
  parameter {
    name  = "autovacuum_max_workers"
    value = "4"
  }
  parameter {
    name  = "autovacuum_naptime"
    value = "30s"
  }
}

resource "aws_iam_role" "rds_monitoring" {
  name = "aircargo-rds-monitoring"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = { Service = "monitoring.rds.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# ─── ECR Repositories ───
resource "aws_ecr_repository" "services" {
  for_each = toset([
    "aircargo-auth-service",
    "aircargo-flight-service",
    "aircargo-booking-service",
    "aircargo-mawb-service",
    "aircargo-warehouse-service",
    "aircargo-uld-service",
    "aircargo-load-planning-service",
    "aircargo-export-service",
    "aircargo-notification-service",
    "aircargo-gateway",
    "aircargo-frontend"
  ])
  
  name                 = each.key
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
  encryption_configuration {
    encryption_type = "AES256"
  }
  
  lifecycle_policy {
    policy = jsonencode({
      rules = [{
        rulePriority = 1
        description  = "Keep last 10 images"
        selection = {
          tagStatus     = "any"
          countType     = "imageCountMoreThan"
          countNumber   = 10
        }
        action = { type = "expire" }
      }, {
        rulePriority = 2
        description  = "Delete untagged after 7 days"
        selection = {
          tagStatus     = "untagged"
          countType     = "sinceImagePushed"
          countUnit     = "days"
          countNumber   = 7
        }
        action = { type = "expire" }
      }]
    })
  }
}

resource "aws_ecr_lifecycle_policy" "services" {
  for_each = aws_ecr_repository.services
  repository = each.value.name
  policy = each.value.lifecycle_policy[0].policy
}

# ─── Route53 + ACM ───
resource "aws_route53_zone" "aircargo" {
  name = var.domain_name
  
  tags = {
    Name = "aircargo-zone"
  }
}

resource "aws_acm_certificate" "aircargo" {
  domain_name       = var.domain_name
  subject_alternative_names = ["*.${var.domain_name}", "api.${var.domain_name}"]
  validation_method = "DNS"
  
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "acm_validation" {
  for_each = {
    for dvo in aws_acm_certificate.aircargo.domain_validation_options :
    dvo.domain => {
      name   = dvo.resource_record_name
      type   = dvo.resource_record_type
      value  = dvo.resource_record_value
    }
  }
  
  zone_id = aws_route53_zone.aircargo.zone_id
  name    = each.value.name
  type    = each.value.type
  ttl     = 60
  records = [each.value.value]
}

resource "aws_acm_certificate_validation" "aircargo" {
  certificate_arn         = aws_acm_certificate.aircargo.arn
  validation_record_fqdns = [for record in aws_route53_record.acm_validation : record.fqdn]
}

# ─── RabbitMQ on EKS (via Helm) ───
resource "helm_release" "rabbitmq" {
  name       = "rabbitmq"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "rabbitmq"
  namespace  = "aircargo"
  version    = "12.5.0"
  create_namespace = true
  
  set {
    name  = "auth.username"
    value = "aircargo"
  }
  set {
    name  = "auth.password"
    value = var.rabbitmq_password
  }
  set {
    name  = "auth.erlangCookie"
    value = var.erlang_cookie
  }
  set {
    name  = "replicaCount"
    value = "3"
  }
  set {
    name  = "clustering.enabled"
    value = "true"
  }
  set {
    name  = "persistence.enabled"
    value = "true"
  }
  set {
    name  = "persistence.size"
    value = "20Gi"
  }
  set {
    name  = "resources.requests.memory"
    value = "1Gi"
  }
  set {
    name  = "resources.requests.cpu"
    value = "500m"
  }
  set {
    name  = "resources.limits.memory"
    value = "2Gi"
  }
  set {
    name  = "resources.limits.cpu"
    value = "2000m"
  }
  set {
    name = "service.type"
    value = "ClusterIP"
  }
  set {
    name  = "metrics.enabled"
    value = "true"
  }
  set {
    name  = "metrics.serviceMonitor.enabled"
    value = "true"
  }
  set {
    name  = "metrics.prometheusRule.enabled"
    value = "true"
  }
}

# ─── Security Group for EKS Nodes (reference) ───
data "aws_security_group" "eks_nodes" {
  filter {
    name   = "tag:Name"
    values = ["*aircargo*-node*"]
  }
}

# ─── Outputs ───
output "rds_endpoint" {
  value = aws_db_instance.aircargo.endpoint
}

output "rds_port" {
  value = aws_db_instance.aircargo.port
}

output "db_name" {
  value = aws_db_instance.aircargo.db_name
}

output "ecr_repositories" {
  value = { for k, v in aws_ecr_repository.services : k => v.repository_url }
}

output "domain_name" {
  value = var.domain_name
}

output "acm_certificate_arn" {
  value = aws_acm_certificate_validation.aircargo.certificate_arn
}

output "route53_zone_id" {
  value = aws_route53_zone.aircargo.zone_id
}

output "rabbitmq_endpoint" {
  value = "rabbitmq.aircargo.svc.cluster.local:5672"
}
TFEOF

cat > "$TF_DIR/variables.tf" <<'TFEOF'
variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS region"
}

variable "domain_name" {
  type        = string
  description = "Domain name (e.g., aircargo.example.com)"
}

variable "db_password" {
  type        = string
  description = "RDS master password (min 8 chars)"
  sensitive   = true
}

variable "rabbitmq_password" {
  type        = string
  description = "RabbitMQ admin password"
  sensitive   = true
}

variable "erlang_cookie" {
  type        = string
  description = "Erlang cookie for RabbitMQ clustering"
  sensitive   = true
}
TFEOF

cat > "$TF_DIR/terraform.tfvars.example" <<'TFEOF'
aws_region       = "us-east-1"
domain_name      = "aircargo.example.com"
db_password      = "CHANGE_ME_SECURE_PASSWORD_MIN_8_CHARS"
rabbitmq_password = "CHANGE_ME_RABBITMQ_PASSWORD"
erlang_cookie    = "CHANGE_ME_ERLANG_COOKIE_32_CHARS"
TFEOF

# Check for tfvars
if [[ ! -f "$TF_DIR/terraform.tfvars" ]]; then
  error "Archivo $TF_DIR/terraform.tfvars no existe"
  info "Crea uno basado en terraform.tfvars.example con valores reales"
  exit 1
fi

# Initialize Terraform
log "Inicializando Terraform..."
terraform -chdir="$TF_DIR" init -upgrade

# Plan
log "Generando plan Terraform..."
terraform -chdir="$TF_DIR" plan -out=tfplan

# Show plan summary
log "Resumen del plan:"
terraform -chdir="$TF_DIR" show -no-color tfplan | head -100

# Confirm apply
echo ""
warn "ESTO CREARÁ RECURSOS EN AWS (COSTOS REALES ~$500-1000/mes)"
read -p "¿Aplicar plan? (s/N): " confirm
[[ "$confirm" =~ ^[Ss]$ ]] || { error "Cancelado"; exit 1; }

# Apply
log "Aplicando Terraform..."
terraform -chdir="$TF_DIR" apply tfplan

# Save outputs
log "Guardando outputs..."
terraform -chdir="$TF_DIR" output -json > "$TF_DIR/outputs.json"

# Extract key values for next steps
RDS_ENDPOINT=$(terraform -chdir="$TF_DIR" output -raw rds_endpoint)
RDS_PORT=$(terraform -chdir="$TF_DIR" output -raw rds_port)
DB_NAME=$(terraform -chdir="$TF_DIR" output -raw db_name)
ACM_CERT_ARN=$(terraform -chdir="$TF_DIR" output -raw acm_certificate_arn)
R53_ZONE_ID=$(terraform -chdir="$TF_DIR" output -raw route53_zone_id)

log "Infraestructura provisionada exitosamente:"
log "  RDS Endpoint: $RDS_ENDPOINT:$RDS_PORT"
log "  DB Name: $DB_NAME"
log "  ACM Cert ARN: $ACM_CERT_ARN"
log "  Route53 Zone ID: $R53_ZONE_ID"

# Create k8s secret with RDS credentials (temporary, will be replaced by External Secrets)
log "Creando secret temporal en k8s..."
kubectl create secret generic aircargo-secrets -n aircargo \
  --from-literal=POSTGRES_HOST="$RDS_ENDPOINT" \
  --from-literal=POSTGRES_PORT="$RDS_PORT" \
  --from-literal=POSTGRES_DB="$DB_NAME" \
  --from-literal=POSTGRES_USER=aircargo_user \
  --from-literal=POSTGRES_PASSWORD="$(terraform -chdir="$TF_DIR" output -raw db_password 2>/dev/null || grep db_password "$TF_DIR/terraform.tfvars" | cut -d'=' -f2 | tr -d ' ')" \
  --from-literal=RABBITMQ_HOST="rabbitmq.aircargo.svc.cluster.local" \
  --from-literal=RABBITMQ_PORT="5672" \
  --from-literal=RABBITMQ_USER=aircargo \
  --from-literal=RABBITMQ_PASSWORD="$(grep rabbitmq_password "$TF_DIR/terraform.tfvars" | cut -d'=' -f2 | tr -d ' ')" \
  --from-literal=JWT_SECRET="$(openssl rand -base64 64)" \
  --from-literal=APP_ENCRYPTION_KEY="$(openssl rand -base64 32)" \
  --dry-run=client -o yaml | kubectl apply -f -

log "=== PASO 1 COMPLETADO ==="
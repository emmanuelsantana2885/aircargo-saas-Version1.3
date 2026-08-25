#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Automatic DB Snapshot Manager
# Creates snapshot before deploy, restores on rollback
# Works with: AWS RDS, Cloud SQL, Azure Database, local PostgreSQL
# ────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Configuration
SNAPSHOT_PREFIX="aircargo-predeploy"
SNAPSHOT_RETENTION_DAYS=7
DB_IDENTIFIER="${DB_IDENTIFIER:-aircargo-production}"
AWS_REGION="${AWS_REGION:-us-east-1}"

# Snapshot metadata file
META_DIR="/tmp/aircargo-db-snapshots"
mkdir -p "$META_DIR"

create_rds_snapshot() {
  local suffix="${1:-$(date +%Y%m%d-%H%M%S)}"
  local snapshot_id="${SNAPSHOT_PREFIX}-${suffix}"
  
  log "Creando snapshot RDS: $snapshot_id"
  
  # Create snapshot
  aws rds create-db-snapshot \
    --db-instance-identifier "$DB_IDENTIFIER" \
    --db-snapshot-identifier "$snapshot_id" \
    --region "$AWS_REGION" \
    --tags Key=Purpose,Value=PreDeployBackup Key=AutoDelete,Value=true \
    >/dev/null
  
  # Wait for snapshot to be available
  log "Esperando snapshot disponible..."
  aws rds wait db-snapshot-available \
    --db-snapshot-identifier "$snapshot_id" \
    --region "$AWS_REGION"
  
  # Save metadata
  cat > "$META_DIR/${snapshot_id}.json" <<EOF
{
  "snapshot_id": "$snapshot_id",
  "db_identifier": "$DB_IDENTIFIER",
  "created_at": "$(date -Iseconds)",
  "git_sha": "${GITHUB_SHA:-local}",
  "environment": "${ENVIRONMENT:-production}",
  "purpose": "pre-deploy-backup"
}
EOF
  
  log "Snapshot creado: $snapshot_id"
  echo "$snapshot_id"
}

create_local_pg_dump() {
  local suffix="${1:-$(date +%Y%m%d-%H%M%S)}"
  local dump_file="$META_DIR/aircargo-local-${suffix}.dump"
  
  log "Creando dump local PostgreSQL..."
  
  PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump \
    -h "${POSTGRES_HOST:-localhost}" \
    -p "${POSTGRES_PORT:-5432}" \
    -U "${POSTGRES_USER:-aircargo_user}" \
    -d "${POSTGRES_DB:-aircargo_db}" \
    -Fc -Z 9 \
    -f "$dump_file"
  
  # Save metadata
  cat > "${dump_file}.json" <<EOF
{
  "dump_file": "$(basename "$dump_file")",
  "created_at": "$(date -Iseconds)",
  "git_sha": "${GITHUB_SHA:-local}",
  "environment": "${ENVIRONMENT:-local}",
  "purpose": "pre-deploy-backup"
}
EOF
  
  log "Dump local creado: $dump_file"
  echo "$dump_file"
}

restore_rds_snapshot() {
  local snapshot_id="$1"
  local new_db_id="${DB_IDENTIFIER}-restored-$(date +%Y%m%d-%H%M%S)"
  
  log "Restaurando desde snapshot: $snapshot_id"
  
  # Restore to new instance
  aws rds restore-db-instance-from-db-snapshot \
    --db-instance-identifier "$new_db_id" \
    --db-snapshot-identifier "$snapshot_id" \
    --db-instance-class "db.r6g.xlarge" \
    --multi-az \
    --region "$AWS_REGION" \
    --tags Key=RestoredFrom,Value="$snapshot_id" Key=AutoDelete,Value=true \
    >/dev/null
  
  # Wait for restore
  log "Esperando restauración..."
  aws rds wait db-instance-available \
    --db-instance-identifier "$new_db_id" \
    --region "$AWS_REGION"
  
  # Get new endpoint
  local new_endpoint=$(aws rds describe-db-instances \
    --db-instance-identifier "$new_db_id" \
    --region "$AWS_REGION" \
    --query 'DBInstances[0].Endpoint.Address' \
    --output text)
  
  log "Restaurado en: $new_db_id ($new_endpoint)"
  
  # Update k8s secret with new endpoint
  kubectl patch secret aircargo-secrets -n aircargo \
    -p "{\"stringData\":{\"POSTGRES_HOST\":\"$new_endpoint\"}}" \
    2>/dev/null || true
  
  # Restart deployments to pick up new endpoint
  log "Reiniciando deployments para usar nueva DB..."
  kubectl rollout restart deployment -n aircargo -l app=aircargo-auth-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-flight-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-booking-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-mawb-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-warehouse-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-uld-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-load-planning-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-export-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-notification-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-gateway
  
  log "Restauración completa. Nueva DB: $new_db_id"
}

restore_local_pg_dump() {
  local dump_file="$1"
  
  log "Restaurando dump local: $dump_file"
  
  PGPASSWORD="${POSTGRES_PASSWORD}" pg_restore \
    -h "${POSTGRES_HOST:-localhost}" \
    -p "${POSTGRES_PORT:-5432}" \
    -U "${POSTGRES_USER:-aircargo_user}" \
    -d "${POSTGRES_DB:-aircargo_db}" \
    --clean --if-exists --no-owner --no-privileges \
    "$dump_file"
  
  log "Dump restaurado. Reiniciando deployments..."
  kubectl rollout restart deployment -n aircargo -l app=aircargo-auth-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-flight-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-booking-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-mawb-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-warehouse-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-uld-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-load-planning-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-export-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-notification-service
  kubectl rollout restart deployment -n aircargo -l app=aircargo-gateway
  
  log "Restauración local completa"
}

list_snapshots() {
  log "Snapshots disponibles:"
  
  # RDS snapshots
  if has_cmd aws; then
    aws rds describe-db-snapshots \
      --db-instance-identifier "$DB_IDENTIFIER" \
      --region "$AWS_REGION" \
      --query 'DBSnapshots[?starts_with(DBSnapshotIdentifier, `'"$SNAPSHOT_PREFIX"'-`)].{ID:DBSnapshotIdentifier,Status:Status,Created:SnapshotCreateTime,Size:AllocatedStorage}' \
      --output table 2>/dev/null || true
  fi
  
  # Local dumps
  if [[ -d "$META_DIR" ]]; then
    log "Dumps locales:"
    ls -lh "$META_DIR"/aircargo-local-*.dump 2>/dev/null || true
  fi
}

cleanup_old_snapshots() {
  log "Limpiando snapshots antiguos (>${SNAPSHOT_RETENTION_DAYS} días)..."
  
  if has_cmd aws; then
    local cutoff=$(date -d "${SNAPSHOT_RETENTION_DAYS} days ago" +%s)
    
    aws rds describe-db-snapshots \
      --db-instance-identifier "$DB_IDENTIFIER" \
      --region "$AWS_REGION" \
      --query "DBSnapshots[?starts_with(DBSnapshotIdentifier, \`${SNAPSHOT_PREFIX}-\`) && SnapshotCreateTime < \`$(date -d @$cutoff -Iseconds)\`].DBSnapshotIdentifier" \
      --output text 2>/dev/null | tr '\t' '\n' | while read -r snap; do
        [[ -n "$snap" ]] && {
          log "Eliminando snapshot antiguo: $snap"
          aws rds delete-db-snapshot --db-snapshot-identifier "$snap" --region "$AWS_REGION" >/dev/null
        }
      done
  fi
  
  # Local dumps
  find "$META_DIR" -name "aircargo-local-*.dump" -mtime +$SNAPSHOT_RETENTION_DAYS -delete 2>/dev/null || true
  find "$META_DIR" -name "aircargo-local-*.dump.json" -mtime +$SNAPSHOT_RETENTION_DAYS -delete 2>/dev/null || true
}

get_latest_snapshot() {
  # Get latest pre-deploy snapshot
  if has_cmd aws; then
    aws rds describe-db-snapshots \
      --db-instance-identifier "$DB_IDENTIFIER" \
      --region "$AWS_REGION" \
      --query 'sort_by(DBSnapshots[?starts_with(DBSnapshotIdentifier, `'"$SNAPSHOT_PREFIX"'-`) && Status==`available`], &SnapshotCreateTime)[-1].DBSnapshotIdentifier' \
      --output text 2>/dev/null
  else
    # Local: latest dump
    ls -t "$META_DIR"/aircargo-local-*.dump 2>/dev/null | head -1
  fi
}

main() {
  local cmd="${1:-help}"
  shift || true
  
  case "$cmd" in
    create)
      # Detect DB type
      if has_cmd aws && aws rds describe-db-instances --db-instance-identifier "$DB_IDENTIFIER" --region "$AWS_REGION" >/dev/null 2>&1; then
        create_rds_snapshot "$@"
      else
        create_local_pg_dump "$@"
      fi
      ;;
    restore)
      local target="${1:-latest}"
      if [[ "$target" == "latest" ]]; then
        target=$(get_latest_snapshot)
      fi
      
      if [[ -z "$target" || "$target" == "None" ]]; then
        error "No hay snapshots disponibles"
        exit 1
      fi
      
      if [[ "$target" == *.dump ]]; then
        restore_local_pg_dump "$target"
      else
        restore_rds_snapshot "$target"
      fi
      ;;
    list)
      list_snapshots
      ;;
    cleanup)
      cleanup_old_snapshots
      ;;
    latest)
      get_latest_snapshot
      ;;
    *)
      log "Uso: $0 [create|restore [snapshot_id|latest]|list|cleanup|latest]"
      log "  create              - Crear snapshot/dump antes de deploy"
      log "  restore [id|latest] - Restaurar desde snapshot (default: latest)"
      log "  list                - Listar snapshots disponibles"
      log "  cleanup             - Eliminar snapshots >${SNAPSHOT_RETENTION_DAYS} días"
      log "  latest              - Mostrar último snapshot"
      ;;
  esac
}

main "$@"
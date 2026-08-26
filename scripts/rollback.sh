#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Protocolo de Rollback Aircargo
#
#   Uso: ./scripts/rollback.sh [--pre-deploy|--post-deploy|--emergency|--list|--restore FILE]
#
#   Flujo del protocolo:
#   1. PRE-DEPLOY : backup automático ANTES de cualquier despliegue/actualización
#   2. ROLLBACK   : si la nueva versión falla, se restaura el pre-deploy:
#                   - flag en /tmp/aircargo-rollback-flag → start-all.sh lo detecta,
#                     restaura automáticamente y elimina el flag al arrancar.
#                   - o restauración inmediata con --restore <archivo>
#   3. POST-DEPLOY: backup tras un deploy exitoso (punto de restauración estable)
# ────────────────────────────────────────────────────────────────
set -euo pipefail

AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$AIR_ROOT/.env"

PG_HOST="${POSTGRES_HOST:-127.0.0.1}"
[ "$PG_HOST" = "localhost" ] && PG_HOST="127.0.0.1"
PG_PORT="${POSTGRES_PORT:-5432}"
DB_NAME="${POSTGRES_DB:-aircargo}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/aircargo-backups}"
ROLLBACK_FLAG="/tmp/aircargo-rollback-flag"
ROLLBACK_LOG="$BACKUP_DIR/rollback.log"

mkdir -p "$BACKUP_DIR"
log() { echo "[$(date '+%F %T')] $*" | tee -a "$ROLLBACK_LOG"; }

list_backups() {
  log "📋 Backups disponibles en $BACKUP_DIR:"
  if ls "$BACKUP_DIR"/*.dump >/dev/null 2>&1; then
    ls -lht "$BACKUP_DIR"/*.dump | awk '{printf "  %-70s %10s  %s %s %s\n", $9, $5, $6, $7, $8}'
  else
    log "  (ninguno)"
  fi
}

restore_backup() {
  local file="$1"
  [ -f "$file" ] || { log "❌ No existe: $file"; exit 1; }
  log "🔄 Restaurando $file sobre la BD '$DB_NAME'..."
  export PGPASSWORD="$POSTGRES_PASSWORD"
  # --clean --if-exists: borra objetos existentes antes de crearlos (sin errores si no existen)
  pg_restore -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" \
             -d "$DB_NAME" --clean --if-exists --no-owner "$file"
  log "✅ Restauración completada desde $(basename "$file")"
}

pre_deploy_backup() {
  log "📦 BACKUP PRE-DEPLOY iniciado"
  "$AIR_ROOT/scripts/db-backup.sh" pre-deploy
  log "✅ Backup pre-deploy completado — punto de restauración para rollback guardado"
}

post_deploy_backup() {
  log "📦 BACKUP POST-DEPLOY iniciado"
  "$AIR_ROOT/scripts/db-backup.sh" post-deploy
  log "✅ Backup post-deploy completado"
}

emergency_rollback() {
  log "🚨 ROLLBACK DE EMERGENCIA iniciado"
  # 1) Proteger el estado actual por si acaso (nunca se sabe)
  pre_deploy_backup || log "⚠️  El backup de protección falló — continuando con el flag"
  # 2) Crear flag → el siguiente arranque (start-all.sh) auto-restaura
  echo "$(date +%s)" > "$ROLLBACK_FLAG"
  log "📌 Flag de rollback creado: $ROLLBACK_FLAG"
  log "➡️  Ejecuta ahora las acciones de rollback de código (git checkout <tag>, etc.)"
  log "➡️  Al arrancar con ./start-all.sh se detectará el flag y se auto-restaurará"
  log "    el último backup pre-deploy/manual disponible."
  # Mostrar cuál backup se usará
  local latest
  latest=$(ls -t "$BACKUP_DIR"/*.dump 2>/dev/null | head -1 || true)
  [ -n "$latest" ] && log "🎯 Backup que se restaurará: $latest"
}

case "${1:-}" in
  --pre-deploy)   pre_deploy_backup ;;
  --post-deploy)  post_deploy_backup ;;
  --emergency)    emergency_rollback ;;
  --list)         list_backups ;;
  --restore)
    [ -n "${2:-}" ] || { echo "❌ Uso: $0 --restore <archivo.dump>"; exit 1; }
    restore_backup "$2"
    ;;
  *)
    echo "Uso: $0 [--pre-deploy|--post-deploy|--emergency|--list|--restore <file>]"
    echo ""
    echo "  --pre-deploy     Backup antes de deploy (punto de rollback)"
    echo "  --post-deploy    Backup después de deploy exitoso"
    echo "  --emergency      Rollback de emergencia + flag para auto-restore en el próximo arranque"
    echo "  --list           Listar backups disponibles"
    echo "  --restore FILE   Restaurar un backup específico AHORA"
    exit 1
    ;;
esac
#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Respaldo diario de la BD Aircargo (pg_dump formato custom comprimido)
#
#   Uso manual :  ./scripts/db-backup.sh
#   Automático :  systemctl --user start aircargo-backup.service
#
#   Config opcional (.env o entorno):
#     BACKUP_DIR       → destino (default ~/aircargo-backups)
#     BACKUP_KEEP_DAYS → retención (default 30)
# ────────────────────────────────────────────────────────────────
set -euo pipefail

AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$AIR_ROOT/.env"

BACKUP_DIR="${BACKUP_DIR:-$HOME/aircargo-backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-30}"
DB_NAME="${POSTGRES_DB:-aircargo}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d_%H%M%S)"
OUT="$BACKUP_DIR/${DB_NAME}_$STAMP.dump"

export PGPASSWORD="$POSTGRES_PASSWORD"
pg_dump -h "127.0.0.1" \
        -p "${POSTGRES_PORT:-5432}" \
        -U "$POSTGRES_USER" \
        -d "$DB_NAME" \
        -Fc -f "$OUT"

# Retención: borrar respaldos más viejos que KEEP_DAYS
find "$BACKUP_DIR" -name "*.dump" -mtime "+$KEEP_DAYS" -delete 2>/dev/null || true

COUNT=$(find "$BACKUP_DIR" -name "*.dump" | wc -l)
echo "[$(date '+%F %T')] ✓ $OUT ($(du -h "$OUT" | cut -f1)) — $COUNT respaldos retenidos"

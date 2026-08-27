#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Respaldo de la BD Aircargo (pg_dump formato custom comprimido)
#
#   Uso manual :  ./scripts/db-backup.sh [daily|pre-deploy|post-deploy|manual]
#   Automático :  systemctl --user start aircargo-backup.service
#
#   Configuración (persistente en BD, editable por ADMIN/SUPER_USER vía API):
#     backup_config.backup_dir  → destino ('' = $HOME/aircargo-backups)
#     backup_config.keep_days   → retención (default 30)
#     backup_config.compress_level → compresión pg_dump 0-9 (default 6)
#   Variables de entorno (.env):
#     POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
#
#   Copia OFFSITE (protege ante fallo de disco — el backup local vive en la
#   misma máquina que la BD). Se sincroniza tras cada dump exitoso, best-effort:
#     BACKUP_OFFSITE_TARGET  destino con prefijo de modo:
#       rsync:/mnt/usb/aircargo-backups      → rsync a disco local externo (USB/NAS montado)
#       rclone:gdrive:aircargo-backups       → rclone remote (S3/GDrive/OneDrive…)
#     BACKUP_OFFSITE_DIR     subcarpeta dentro del target (default: mismo nombre de BACKUP_DIR)
#   Nota rclone: configurar el remote antes (`rclone config`); rsync/rclone son opcionales.
# ────────────────────────────────────────────────────────────────
set -euo pipefail

AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$AIR_ROOT/.env"

PG_HOST="${POSTGRES_HOST:-127.0.0.1}"
[ "$PG_HOST" = "localhost" ] && PG_HOST="127.0.0.1"
PG_PORT="${POSTGRES_PORT:-5432}"
DB_NAME="${POSTGRES_DB:-aircargo}"
BACKUP_TYPE="${1:-manual}"

BACKUP_DIR="${BACKUP_DIR:-}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-30}"
COMPRESS_LEVEL="${BACKUP_COMPRESS:-6}"

# Cargar config desde BD si está disponible (sin jq: columnas individuales)
load_db_config() {
  command -v psql >/dev/null 2>&1 || return 0
  local q_out
  q_out="$(PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" -d "$DB_NAME" -tAF '|' \
    -c "SELECT COALESCE(backup_dir,''), keep_days, compress_level FROM backup_config WHERE id=1;" 2>/dev/null || true)"
  if [ -n "$q_out" ]; then
    local cfg_dir cfg_keep_cfg cfg_compress
    cfg_dir="$(echo "$q_out" | cut -d'|' -f1)"
    cfg_keep="$(echo "$q_out" | cut -d'|' -f2)"
    cfg_compress="$(echo "$q_out" | cut -d'|' -f3)"
    if [ -n "$cfg_dir" ]; then BACKUP_DIR="$cfg_dir"; fi
    case "$cfg_keep" in ''|*[!0-9]*) ;; *) KEEP_DAYS="$cfg_keep" ;; esac
    case "$cfg_compress" in ''|*[!0-9]*) ;; *) COMPRESS_LEVEL="$cfg_compress" ;; esac
  fi
}
load_db_config

[ -z "$BACKUP_DIR" ] && BACKUP_DIR="$HOME/aircargo-backups"
mkdir -p "$BACKUP_DIR"

STAMP="$(date +%Y%m%d_%H%M%S)"
OUT="$BACKUP_DIR/${DB_NAME}_${BACKUP_TYPE}_$STAMP.dump"

export PGPASSWORD="$POSTGRES_PASSWORD"
START_MS="$(date +%s%3N)"
pg_dump -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" -d "$DB_NAME" \
        -Fc -Z "$COMPRESS_LEVEL" -f "$OUT"
END_MS="$(date +%s%3N)"
DURATION=$((END_MS - START_MS))

# Retención: borrar respaldos más viejos que KEEP_DAYS
find "$BACKUP_DIR" -name "*.dump" -mtime "+$KEEP_DAYS" -delete 2>/dev/null || true

COUNT=$(find "$BACKUP_DIR" -name "*.dump" | wc -l)
SIZE=$(du -h "$OUT" | cut -f1)
echo "[$(date '+%F %T')] ✓ $OUT ($SIZE) — $COUNT respaldos retenidos (tipo: $BACKUP_TYPE)"

# Registrar en historial (best-effort; la BD puede estar caída justamente)
if command -v psql >/dev/null 2>&1; then
  PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" -d "$DB_NAME" -q <<SQL >/dev/null 2>&1 || true
INSERT INTO backup_history (file_name, file_path, size_bytes, backup_type, status, duration_ms, completed_at)
VALUES ('$(basename "$OUT")', '$OUT', $(stat -c%s "$OUT"), '${BACKUP_TYPE^^}', 'SUCCESS', $DURATION, now());
SQL
fi

# ── Copia OFFSITE (best-effort: nunca falla el backup local) ──────────
sync_offsite() {
  local target="${BACKUP_OFFSITE_TARGET:-}"
  [ -z "$target" ] && return 0

  local mode="${target%%:*}"
  local rest="${target#*:}"

  case "$mode" in
    rsync)
      if ! command -v rsync >/dev/null 2>&1; then
        echo "[$(date '+%F %T')] ⚠ OFFSITE: rsync no instalado — copia omitida ($target)"
        return 0
      fi
      local dest="${rest%/}/${BACKUP_OFFSITE_DIR:-$(basename "$BACKUP_DIR")}"
      mkdir -p "$dest" 2>/dev/null || { echo "[$(date '+%F %T')] ⚠ OFFSITE: no se puede crear $dest (¿disco montado?)"; return 0; }
      if rsync -a --delete --include="*.dump" --exclude="*" "$BACKUP_DIR/" "$dest/" 2>/dev/null; then
        echo "[$(date '+%F %T')] ✓ OFFSITE: sincronizado a $dest"
      else
        echo "[$(date '+%F %T')] ⚠ OFFSITE: rsync falló hacia $dest (revisar montaje/red)"
      fi
      ;;
    rclone)
      if ! command -v rclone >/dev/null 2>&1; then
        echo "[$(date '+%F %T')] ⚠ OFFSITE: rclone no instalado — copia omitida ($target)"
        return 0
      fi
      local dest="${rest%/}/$(basename "$BACKUP_DIR")"
      # Solo subimos los dumps vigentes (la retención local ya podó los viejos)
      local failed=0
      for f in "$BACKUP_DIR"/*.dump; do
        [ -e "$f" ] || continue
        rclone copy "$f" "$dest" 2>/dev/null || failed=1
      done
      if [ "$failed" = "0" ]; then
        echo "[$(date '+%F %T')] ✓ OFFSITE: sincronizado a rclone:$dest"
      else
        echo "[$(date '+%F %T')] ⚠ OFFSITE: rclone con errores hacia $dest (revisar remote/conexión)"
      fi
      ;;
    *)
      echo "[$(date '+%F %T')] ⚠ OFFSITE: prefijo desconocido '$mode' (usar rsync: o rclone:) — omitido"
      ;;
  esac
}
sync_offsite
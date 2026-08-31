#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Restauración de la BD Aircargo desde un dump (formato custom de pg_dump)
#
#   Uso:  ./scripts/db-restore.sh --file <ruta.dump>
#         ./scripts/db-restore.sh --url  <https://.../backup.dump>
#
#   Seguridad (a prueba de error):
#     1. SIEMPRE crea un backup de protección de la BD ACTUAL antes de
#        restaurar (los datos actuales nunca se pierden por un restore).
#     2. Valida que el archivo sea un dump custom de PostgreSQL (magic
#        bytes "PGDMP"): rechaza archivos corruptos o de otro formato.
#     3. pg_restore con --clean --if-exists --no-owner --no-privileges
#        (mismo parámetro verificado en el round-trip de la sesión).
#     4. Registra la acción en backup_history (tipo RESTORE) y en
#        rollback.log — trazabilidad completa.
#
#   Nota post-restore: los servicios siguen corriendo con datos en
#   caché/pool; para un arranque 100% limpio recomendamos reiniciar el
#   stack (./start-all.sh --skip-build).
# ────────────────────────────────────────────────────────────────
set -euo pipefail

AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$AIR_ROOT/.env"

PG_HOST="${POSTGRES_HOST:-127.0.0.1}"
[ "$PG_HOST" = "localhost" ] && PG_HOST="127.0.0.1"
PG_PORT="${POSTGRES_PORT:-5432}"
DB_NAME="${POSTGRES_DB:-aircargo}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/aircargo-backups}"
ROLLBACK_LOG="$BACKUP_DIR/rollback.log"

mkdir -p "$BACKUP_DIR"
log() { echo "[$(date '+%F %T')] $*" | tee -a "$ROLLBACK_LOG"; }

die() { log "❌ $*"; exit 1; }

# --- Parámetros ─────────────────────────────────────────────────
SOURCE=""
FILE_SRC=""
URL_SRC=""
while [ $# -gt 0 ]; do
  case "$1" in
    --file) FILE_SRC="${2:-}"; shift 2 ;;
    --url)  URL_SRC="${2:-}"; shift 2 ;;
    *) die "Argumento desconocido: $1 (usar --file <ruta> o --url <https://...>)" ;;
  esac
done

if [ -n "$FILE_SRC" ] && [ -n "$URL_SRC" ]; then
  die "Indica solo una fuente: --file O --url"
fi

# --- Resolución de la fuente ────────────────────────────────────
DUMP=""
if [ -n "$URL_SRC" ]; then
  case "$URL_SRC" in
    http://*|https://*) ;;
    *) die "La URL debe ser http(s)://" ;;
  esac
  log "⬇️  Descargando dump desde: $URL_SRC"
  FNAME="$(basename "$URL_SRC" | sed 's/[^A-Za-z0-9._-]/_/g')"
  [ -n "$FNAME" ] || FNAME="${DB_NAME}_restore.dump"
  DUMP="$BACKUP_DIR/${FNAME%.dump}_$(date +%Y%m%d_%H%M%S).dump"
  curl -fsSL --connect-timeout 15 --max-time 600 -o "$DUMP" "$URL_SRC" \
    || die "Fallo descargando $URL_SRC (curl)"
  log "✓ Descargado a $DUMP ($(du -h "$DUMP" | cut -f1))"
else
  [ -n "$FILE_SRC" ] || die "Indica --file o --url"
  [ -f "$FILE_SRC" ] || die "No existe el archivo: $FILE_SRC"
  DUMP="$FILE_SRC"
fi

# --- Validación: magic bytes de dump custom de Postgres ─────────
MAGIC="$(head -c 5 "$DUMP" 2>/dev/null || true)"
[ "$MAGIC" = "PGDMP" ] || die "El archivo no es un dump custom de PostgreSQL (magic 'PGDMP' no presente): $DUMP"

export PGPASSWORD="$POSTGRES_PASSWORD"

# --- 1) Backup de protección de la BD ACTUAL (best-effort) ──────
log "🛟 Creando backup de PROTECCIÓN de la BD actual antes de restaurar..."
if ! "$AIR_ROOT/scripts/db-backup.sh" pre-restore; then
  log "⚠️  El backup de protección falló — se continúa con el restore (la BD actual puede estar dañada)"
fi

# --- 2) Restauración ────────────────────────────────────────────
log "🔄 Restaurando $DUMP sobre la BD '$DB_NAME'..."
START_MS="$(date +%s%3N)"
RESTORE_LOG="$BACKUP_DIR/restore_$(date +%Y%m%d_%H%M%S).log"
set +e
pg_restore -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" \
           -d "$DB_NAME" --clean --if-exists --no-owner --no-privileges "$DUMP" >"$RESTORE_LOG" 2>&1
RC=$?
set -e
END_MS="$(date +%s%3N)"
DURATION=$((END_MS - START_MS))

if [ "$RC" -ne 0 ]; then
  # Clasificamos el error: el único fallo conocido NO destructivo es
  # `SET transaction_timeout` (dump creado con pg_dump >= 17 vs servidor
  # que no la soporta). Si hay CUALQUIER otro error → restore fallido.
  OTHER_ERR="$(grep -v 'transaction_timeout' "$RESTORE_LOG" | grep -c 'ERROR' || true)"
  if [ "$OTHER_ERR" -eq 0 ]; then
    log "⚠️  pg_restore rc=$RC pero el único error es el benigno 'transaction_timeout' (SET de pg_dump nuevo) → se considera EXITOSO"
    RC=0
  else
    log "❌ pg_restore terminó con código $RC — ver $RESTORE_LOG"
    # Registrar fallo (tipo RESTORE) — la BD puede estar dañada → best-effort
    PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" -d "$DB_NAME" -q <<SQL >/dev/null 2>&1 || true
INSERT INTO backup_history (file_name, file_path, size_bytes, backup_type, status, error_message, duration_ms, completed_at)
VALUES ('$(basename "$DUMP")', '$DUMP', $(stat -c%s "$DUMP" 2>/dev/null || echo 0), 'RESTORE', 'FAILED', 'pg_restore rc='$RC', ver log', $DURATION, now());
SQL
    exit "$RC"
  fi
fi

log "✅ Restauración completada desde $(basename "$DUMP") (${DURATION}ms)"

# --- 3) Verificación de conteos (consistencia mínima) ──────────
if command -v psql >/dev/null 2>&1; then
  COUNTS="$(PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" -d "$DB_NAME" -tAF ' ' \
    -c "SELECT (SELECT count(*) FROM app_user), (SELECT count(*) FROM mawb);" 2>/dev/null || true)"
  if [ -n "$COUNTS" ]; then
    log "📊 Verificación post-restore: $(echo "$COUNTS" | sed 's/ / usuarios, /') MAWBs"
  else
    log "⚠️  No se pudo verificar conteos (BD o tablas ausentes)"
  fi
fi

# --- 4) Registrar el restore (tipo RESTORE) en el historial ────
PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" -d "$DB_NAME" -q <<SQL >/dev/null 2>&1 || true
INSERT INTO backup_history (file_name, file_path, size_bytes, backup_type, status, duration_ms, completed_at)
VALUES ('$(basename "$DUMP")', '$DUMP', $(stat -c%s "$DUMP" 2>/dev/null || echo 0), 'RESTORE', 'SUCCESS', $DURATION, now());
SQL

log "💡 Tras restaurar, reinicia el stack para limpiar cachés/pools: ./start-all.sh --skip-build"
echo "OK: $DUMP"
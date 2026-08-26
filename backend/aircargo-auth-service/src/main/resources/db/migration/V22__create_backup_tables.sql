-- ────────────────────────────────────────────────────────────────
-- Tablas de configuración e historial de backups
-- ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS backup_config (
    id              INTEGER PRIMARY KEY DEFAULT 1,
    backup_dir      VARCHAR(500) NOT NULL,
    keep_days       INTEGER NOT NULL DEFAULT 30,
    compress_level  INTEGER NOT NULL DEFAULT 6,
    auto_backup_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    auto_backup_schedule VARCHAR(50) DEFAULT '0 2 * * ?',
    notify_on_success   BOOLEAN NOT NULL DEFAULT FALSE,
    notify_on_failure   BOOLEAN NOT NULL DEFAULT TRUE,
    notification_emails TEXT,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT single_config CHECK (id = 1)
);

-- Configuración por defecto (idempotente: NO sobrescribe la carpeta ya configurada por el admin)
-- backup_dir vacío = usar default del sistema ($HOME/aircargo-backups)
INSERT INTO backup_config (id, backup_dir, keep_days, compress_level, auto_backup_enabled, auto_backup_schedule, notify_on_success, notify_on_failure, notification_emails)
SELECT 1, '', 30, 6, TRUE, '0 2 * * ?', FALSE, TRUE, ''
WHERE NOT EXISTS (SELECT 1 FROM backup_config WHERE id = 1);

CREATE TABLE IF NOT EXISTS backup_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name       VARCHAR(255) NOT NULL,
    file_path       VARCHAR(1000) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    backup_type     VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    error_message   TEXT,
    duration_ms     BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_backup_history_created ON backup_history (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_backup_history_type ON backup_history (backup_type);
CREATE INDEX IF NOT EXISTS idx_backup_history_status ON backup_history (status);

-- Permisos
GRANT SELECT, INSERT, UPDATE ON backup_config TO aircargo_user;
GRANT SELECT, INSERT ON backup_history TO aircargo_user;
-- ────────────────────────────────────────────────────────────────
-- Política MFA: re-enrolamiento forzado al reiniciar/actualizar
-- la aplicación + expiración automática por antigüedad.
-- ────────────────────────────────────────────────────────────────
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS mfa_enrolled_at TIMESTAMPTZ;

-- mfa_policy es una fila singleton (id=1): el epoch de reset global.
-- last_reset_at = instante del último reinicio/actualización (los
-- usuarios enrolados ANTES de ese instante deben re-configurar MFA).
CREATE TABLE IF NOT EXISTS mfa_policy (
    id               INTEGER PRIMARY KEY DEFAULT 1,
    last_reset_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    reset_on_startup BOOLEAN NOT NULL DEFAULT TRUE,
    max_age_days     INTEGER NOT NULL DEFAULT 7,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT single_mfa_policy CHECK (id = 1)
);

INSERT INTO mfa_policy (id, last_reset_at, reset_on_startup, max_age_days)
SELECT 1, now(), TRUE, 7
WHERE NOT EXISTS (SELECT 1 FROM mfa_policy WHERE id = 1);

GRANT SELECT, INSERT, UPDATE ON mfa_policy TO aircargo_user;
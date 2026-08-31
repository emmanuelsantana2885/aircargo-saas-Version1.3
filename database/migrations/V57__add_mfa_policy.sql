-- ═══════════════════════════════════════════════════════════════
-- V24 — Política MFA: re-enrolamiento forzado en reinicio/actualización
--        + expiración automática por antigüedad (max_age_days).
--
-- 1) mfa_enrolled_at en app_user: cuándo el usuario configuró su MFA
--    por última vez (lo setea MfaService.enableMfa / enrollMfaEnable).
-- 2) mfa_policy (singleton id=1): last_reset_at = el instante del último
--    reinicio/actualización de la app. Todo MFA enrolado ANTES de ese
--    instante se considera caduco → el login exige re-enrolar.
-- 3) Regla de antigüedad: si pasan max_age_days (default 7) sin reinicio,
--    los MFA enrolados antes de now()-max_age_days también caducan.
--
-- El epoch lo mueve MfaStartupResetRunner en cada arranque del
-- auth-service (solo si app.mfa.reset-on-startup=true). Los filtros JWT
-- ya rechazan los enrollToken como Bearer.
-- 100% idempotente (ON CONFLICT / WHERE NOT EXISTS).
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS mfa_enrolled_at TIMESTAMPTZ;

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
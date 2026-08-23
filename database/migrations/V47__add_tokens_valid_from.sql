-- Revocación central de tokens: los tokens (access y refresh) emitidos
-- ANTES de esta fecha dejan de ser válidos. Null = sin restricción.
-- Se adelanta en: bloqueo, desactivación, reset/cambio de contraseña,
-- enable/disable MFA. Ver TokenRevocationService.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS tokens_valid_from TIMESTAMPTZ;

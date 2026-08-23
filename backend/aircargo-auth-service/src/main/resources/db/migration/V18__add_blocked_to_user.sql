-- Idempotente y seguro en BDs vacías (la tabla puede no existir aún:
-- V20__baseline_auth_schema la crea completa).
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'app_user') THEN
        ALTER TABLE app_user ADD COLUMN IF NOT EXISTS blocked BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

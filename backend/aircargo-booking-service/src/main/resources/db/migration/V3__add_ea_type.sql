-- V3__add_ea_type.sql
-- Add ea_type column to booking table (SKID or BOX)

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ea_type') THEN
        CREATE TYPE ea_type AS ENUM ('SKID', 'BOX');
    END IF;
END $$;

ALTER TABLE booking ADD COLUMN IF NOT EXISTS ea_type VARCHAR(10);

-- Seed: bookings with skids > 0 default to SKID, others default to BOX
UPDATE booking SET ea_type = 'SKID' WHERE skids > 0 AND ea_type IS NULL;
UPDATE booking SET ea_type = 'BOX' WHERE (skids IS NULL OR skids = 0) AND ea_type IS NULL;

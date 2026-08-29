-- Remove door (location) column from ULD table
-- Add destination column
-- confirmed_with remains as text (not dropdown)

ALTER TABLE uld
    DROP COLUMN IF EXISTS door,
    ADD COLUMN IF NOT EXISTS destination VARCHAR(100);

COMMENT ON COLUMN uld.destination IS 'Destino del ULD (ej. MIA, SDQ, etc.)';
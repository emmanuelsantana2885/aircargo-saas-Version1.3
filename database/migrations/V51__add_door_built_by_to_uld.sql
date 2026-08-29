-- Add door (location) and built_by fields to ULD table
-- Allows storing location/built-by separately instead of packing into notes

ALTER TABLE uld
    ADD COLUMN IF NOT EXISTS door VARCHAR(100),
    ADD COLUMN IF NOT EXISTS built_by VARCHAR(100);

COMMENT ON COLUMN uld.door IS 'Ubicación/puerta donde se posiciona el ULD (ej. puerta 4, patio, etc.)';
COMMENT ON COLUMN uld.built_by IS 'Operador que armó/construyó el ULD (Built By)';
-- Add confirmed_with and completed_at fields to ULD table
-- For pallet sheet footer: confirmed_with (dropdown per airline), completed_at (timestamp when ULD completed)

ALTER TABLE uld
    ADD COLUMN IF NOT EXISTS confirmed_with VARCHAR(100),
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN uld.confirmed_with IS 'Fuente de confirmación de peso (dropdown por aerolínea: balanza certificada, proceso W&B, etc.)';
COMMENT ON COLUMN uld.completed_at IS 'Timestamp cuando el ULD pasó a estado completado (BUILT/SEALED/LOADED)';
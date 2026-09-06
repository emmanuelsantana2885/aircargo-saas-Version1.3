-- Sincronizado desde aircargo-uld-service V9__add_status_to_uld_awb.sql
-- Snapshot del estado de la MAWB en uld_awb para manifests/load-planning.
ALTER TABLE uld_awb ADD COLUMN IF NOT EXISTS status VARCHAR(20);
COMMENT ON COLUMN uld_awb.status IS 'Snapshot del estado de la MAWB (sync automatico via aircargo.uld.mawb-sync)';
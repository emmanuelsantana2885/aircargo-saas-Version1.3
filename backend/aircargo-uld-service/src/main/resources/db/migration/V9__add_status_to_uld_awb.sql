-- Add MAWB status snapshot to uld_awb so manifests/load-planning reflect the
-- current MAWB status without cross-service joins. Kept in sync automatically
-- by the mawb.updated event listener (aircargo.uld.mawb-sync queue).
ALTER TABLE uld_awb ADD COLUMN IF NOT EXISTS status VARCHAR(20);
COMMENT ON COLUMN uld_awb.status IS 'Snapshot del estado de la MAWB (sync automatico via aircargo.uld.mawb-sync)';
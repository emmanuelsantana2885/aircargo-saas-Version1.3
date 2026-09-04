-- V58__add_ea_type_to_booking.sql
-- Add ea_type column to booking table (SKID or BOX)

ALTER TABLE booking ADD COLUMN IF NOT EXISTS ea_type VARCHAR(10);

-- Seed: bookings with skids > 0 default to SKID, others default to BOX
UPDATE booking SET ea_type = 'SKID' WHERE skids > 0 AND ea_type IS NULL;
UPDATE booking SET ea_type = 'BOX' WHERE (skids IS NULL OR skids = 0) AND ea_type IS NULL;

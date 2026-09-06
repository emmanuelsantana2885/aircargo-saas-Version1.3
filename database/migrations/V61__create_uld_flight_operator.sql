-- Per-(ULD, flight) operator data: each flight a ULD is used in keeps its own
-- Loaded By / Weighed By / Confirmed With, independent of the ULD's current flight.
CREATE TABLE IF NOT EXISTS uld_flight_operator (
    id             UUID PRIMARY KEY,
    uld_id         UUID NOT NULL REFERENCES uld(id) ON DELETE CASCADE,
    flight_id      UUID NOT NULL,
    loaded_by      VARCHAR(100),
    weighed_by     VARCHAR(100),
    confirmed_with VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_uld_flight_operator UNIQUE (uld_id, flight_id)
);

CREATE INDEX IF NOT EXISTS idx_uld_flight_operator_flight ON uld_flight_operator (flight_id);
CREATE INDEX IF NOT EXISTS idx_uld_flight_operator_uld   ON uld_flight_operator (uld_id);

-- Backfill current assignments from the uld columns so every flight already in
-- use has its operator snapshot (columns on uld are kept, not replaced).
INSERT INTO uld_flight_operator (id, uld_id, flight_id, loaded_by, weighed_by, confirmed_with)
SELECT gen_random_uuid(), u.id, u.flight_id, u.loaded_by, u.weighed_by, u.confirmed_with
FROM uld u
WHERE u.flight_id IS NOT NULL
  AND (u.loaded_by IS NOT NULL OR u.weighed_by IS NOT NULL OR u.confirmed_with IS NOT NULL)
ON CONFLICT (uld_id, flight_id) DO NOTHING;
-- V1__init.sql
-- Initial schema for flight-service (flight and airline tables)

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'flight_status') THEN
        CREATE TYPE flight_status AS ENUM ('SCHEDULED', 'BOARDING', 'DEPARTED', 'ARRIVED', 'CANCELLED', 'DELAYED');
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'aircraft_type') THEN
        CREATE TYPE aircraft_type AS ENUM ('MD11', 'B747', 'B757', 'B737', 'B767', 'B777', 'B727', 'A300', 'A310', 'A330', 'DC8', 'OTHER');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS airline (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    iata_code VARCHAR(3),
    country VARCHAR(60),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS flight (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    airline_id UUID NOT NULL REFERENCES airline(id),
    flight_number VARCHAR(20) NOT NULL,
    origin VARCHAR(3) NOT NULL,
    destination VARCHAR(3) NOT NULL,
    aircraft_reg VARCHAR(20),
    aircraft_type aircraft_type,
    flight_date DATE NOT NULL,
    status flight_status NOT NULL DEFAULT 'SCHEDULED',
    max_payload_kg NUMERIC(10,2),
    total_positions INTEGER,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (airline_id, flight_number, flight_date)
);

CREATE INDEX IF NOT EXISTS idx_flight_airline_id ON flight(airline_id);
CREATE INDEX IF NOT EXISTS idx_flight_flight_date ON flight(flight_date);
CREATE INDEX IF NOT EXISTS idx_flight_status ON flight(status);
CREATE INDEX IF NOT EXISTS idx_flight_airline_date ON flight(airline_id, flight_date);

-- Seed UPS airline (reference UUID; the frontend no longer hardcodes it, it loads from the API)
-- Usamos ON CONFLICT (code): el auth-service (DataSeeder) puede haber insertado YA la aerolínea
-- UPS (con otro id, porque Airline usa @GeneratedValue y descarta el id explícito). Si upsertamos
-- por id, el conflicto real es el UNIQUE de code → falla. Al apuntar a code, es un no-op si existe.
INSERT INTO airline (id, code, name, iata_code, country, is_active, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'UPS',
    'United Parcel Service',
    '5X',
    'USA',
    TRUE,
    now(),
    now()
) ON CONFLICT (code) DO NOTHING;

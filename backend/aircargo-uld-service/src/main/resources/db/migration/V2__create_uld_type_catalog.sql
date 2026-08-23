-- Catálogo dinámico de tipos ULD según normas IATA (ULD Technical Kit / IGOM).
-- Reemplaza la restricción del enum Java UldType: cualquier tipo registrado aquí
-- puede usarse en uld y uld_type_config sin redeploy.
CREATE TABLE IF NOT EXISTS uld_type_catalog (
    id UUID PRIMARY KEY,
    code VARCHAR(5) NOT NULL UNIQUE,
    description VARCHAR(120),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_uld_type_catalog_sort ON uld_type_catalog(sort_order);

-- Tipos ya existentes en el enum original (migrados 1:1)
INSERT INTO uld_type_catalog (id, code, description, is_active, sort_order) VALUES
    (gen_random_uuid(), 'PMC', 'Pallet 96x125 in, contorno 2 (IATA)', TRUE, 10),
    (gen_random_uuid(), 'PAG', 'Pallet 88x125 in, contorno 1 (IATA)', TRUE, 11),
    (gen_random_uuid(), 'PAH', 'Pallet 88x125 in, contorno 4 (IATA)', TRUE, 12),
    (gen_random_uuid(), 'PAJ', 'Pallet 88x125 in, contorno 5 (IATA)', TRUE, 13),
    (gen_random_uuid(), 'PIP', 'Pallet 60x125 in (IATA)', TRUE, 14),
    (gen_random_uuid(), 'AAY', 'Container 60,4x61,5x64 in (IATA)', TRUE, 15),
    (gen_random_uuid(), 'AAZ', 'Container 60,4x61,5x54 in (IATA)', TRUE, 16),
    (gen_random_uuid(), 'AAD', 'Container 60,4x61,5x41 in (IATA)', TRUE, 17),
    (gen_random_uuid(), 'AMP', 'Main deck pallet 125x96 in (IATA)', TRUE, 18),
    (gen_random_uuid(), 'AMJ', 'Main deck pallet 96x125 in (IATA)', TRUE, 19),
    (gen_random_uuid(), 'BULK', 'Carga suelta / bulk (interno)', TRUE, 20)
ON CONFLICT (code) DO NOTHING;

-- Tipos estándar adicionales según IATA ULD Technical Kit
INSERT INTO uld_type_catalog (id, code, description, is_active, sort_order) VALUES
    -- Contenedores certificados lower deck (LD)
    (gen_random_uuid(), 'AKE', 'Contenedor certificado LD3 79x60x56 in (IATA)', TRUE, 30),
    (gen_random_uuid(), 'AKC', 'Contenedor certificado LD1 92x60x64 in (IATA)', TRUE, 31),
    (gen_random_uuid(), 'AKN', 'Contenedor certificado LD1 92x60x63 in (IATA)', TRUE, 32),
    (gen_random_uuid(), 'AKW', 'Contenedor certificado LD1 92x60x62 in (IATA)', TRUE, 33),
    (gen_random_uuid(), 'AMF', 'Contenedor main deck 96x125x86 in (IATA)', TRUE, 34),
    (gen_random_uuid(), 'AQF', 'Contenedor 60,4x61,5x45 in half (IATA)', TRUE, 35),
    (gen_random_uuid(), 'DPE', 'Contenedor no certificado LD2 61x61x46 in (IATA)', TRUE, 36),
    (gen_random_uuid(), 'DQF', 'Contenedor no certificado LD2 61x61x46 in (IATA)', TRUE, 37),
    -- Pallets/plataformas
    (gen_random_uuid(), 'PLA', 'Pallet main deck 125x96 in (IATA)', TRUE, 40),
    (gen_random_uuid(), 'PLB', 'Pallet main deck 125x96 in (IATA)', TRUE, 41),
    (gen_random_uuid(), 'PAL', 'Pallet 88x125 in (IATA)', TRUE, 42),
    (gen_random_uuid(), 'PNE', 'Pallet 60x125 in, contorno B (IATA)', TRUE, 43),
    (gen_random_uuid(), 'PQG', 'Pallet 60x96 in (IATA)', TRUE, 44),
    -- Especiales
    (gen_random_uuid(), 'RKN', 'Contenedor refrigerado/enfriado (IATA)', TRUE, 50),
    (gen_random_uuid(), 'RMP', 'Pallet refrigerado main deck (IATA)', TRUE, 51),
    (gen_random_uuid(), 'VKE', 'Contenedor para animales vivos (IATA)', TRUE, 52),
    (gen_random_uuid(), 'VRA', 'Pallet para animales vivos (IATA)', TRUE, 53),
    (gen_random_uuid(), 'HME', 'Contenedor para caballos (IATA)', TRUE, 54)
ON CONFLICT (code) DO NOTHING;

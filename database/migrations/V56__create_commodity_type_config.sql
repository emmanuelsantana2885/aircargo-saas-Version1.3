-- ═══════════════════════════════════════════════════════════════
-- V23 — Tabla commodity_type_config (catálogo de tipos de mercancía).
--
-- Añadido por la entidad CommodityTypeEntity + CommodityTypeService
-- de auth-service. El esquema ahora vive bajo ddl-auto=validate, así
-- que la tabla debe crearse por migración explícita (antes Hibernate
-- la creaba con ddl-auto=update silenciosamente).
-- 100% idempotente + seed con ON CONFLICT DO NOTHING.
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS commodity_type_config (
    id UUID DEFAULT gen_random_uuid() NOT NULL,
    code VARCHAR(50) NOT NULL,
    label VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    color VARCHAR(20),
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT commodity_type_config_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS commodity_type_config_code_key ON commodity_type_config(code);

-- Seed por defecto (same set que DataSeeder.seedCommodityTypes())
INSERT INTO commodity_type_config (code, label, description, color, sort_order, is_active)
SELECT v.code, v.label, v.description, v.color, v.sort_order, true FROM (VALUES
    ('PERISHABLE',   'PERISHABLE',    'Temperature-sensitive goods', '#ef4444', 1),
    ('DRY_CARGO',    'DRY CARGO',     'General dry cargo',           '#64748b', 2),
    ('ELECTRONICS',  'ELECTRONICS',   'Electronic devices',          '#8b5cf6', 3),
    ('HIGH_VALUES',  'HIGH VALUES',   'High-value items',            '#f59e0b', 4),
    ('CIGARETTES',   'CIGARETTES',    'Cigarette products',          '#78716c', 5),
    ('SMALL_PACKAGES','SMALL PACKAGES','Small package shipments',    '#06b6d4', 6),
    ('WWEF',         'WWEF',          'Worldwide Express Freight',   '#ec4899', 7),
    ('LIVE_PLANTS',  'LIVE PLANTS',   'Live plant transport',        '#22c55e', 8),
    ('GENERAL',      'GENERAL',       'General cargo',               '#94a3b8', 9),
    ('COMAT',        'COMAT',         'Company material',            '#a3a3a3', 10),
    ('FCC',          'FCC',           'Full Container Load',         '#78716c', 11),
    ('EMPTY_ULD',    'EMPTY ULD',     'Empty ULD equipment',         '#d1d5db', 12),
    ('EMPTY_PALLET', 'EMPTY PALLET',  'Empty pallet equipment',      '#d1d5db', 13),
    ('RED_TAG',      'RED TAG',       'Red-tagged cargo',            '#dc2626', 14),
    ('EMPTY_BAGS',   'EMPTY BAGS',    'Empty bags equipment',        '#a3a3a3', 15),
    ('NETS',         'NETS',          'Cargo nets',                  '#52525b', 16),
    ('SDQ_SDF',      'SDQ-SDF',       'Domestic route SDQ to SDF',   '#2563eb', 17),
    ('SDQ_MIA',      'SDQ-MIA',       'Domestic route SDQ to MIA',   '#2563eb', 18)
) AS v(code, label, description, color, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM commodity_type_config);

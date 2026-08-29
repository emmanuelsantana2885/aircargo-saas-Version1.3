-- Confirmed With options per airline (dropdown sources for ULD form)
-- Each airline can define its valid weight confirmation sources (scales, W&B processes, etc.)

CREATE TABLE IF NOT EXISTS uld_confirmed_source (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    airline_id UUID NOT NULL REFERENCES airline(id),
    code VARCHAR(50) NOT NULL,
    description VARCHAR(200) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    UNIQUE (airline_id, code)
);

CREATE INDEX IF NOT EXISTS idx_uld_confirmed_source_airline ON uld_confirmed_source(airline_id);

COMMENT ON TABLE uld_confirmed_source IS 'Fuentes válidas de confirmación de peso por aerolínea (dropdown en ULD form)';
COMMENT ON COLUMN uld_confirmed_source.code IS 'Código interno (ej. SCALE_MAIN, WB_PROCESS, SCALE_ALT)';
COMMENT ON COLUMN uld_confirmed_source.description IS 'Descripción visible en dropdown (ej. Balanza Principal, Proceso W&B Certificado)';

-- Seed data for UPS (airline_id = '00000000-0000-0000-0000-000000000001')
INSERT INTO uld_confirmed_source (airline_id, code, description, is_active, sort_order) VALUES
('00000000-0000-0000-0000-000000000001', 'SCALE_MAIN', 'Balanza Principal Certificada', true, 1),
('00000000-0000-0000-0000-000000000001', 'SCALE_ALT', 'Balanza Alterna Certificada', true, 2),
('00000000-0000-0000-0000-000000000001', 'WB_PROCESS', 'Proceso Weight & Balance Certificado', true, 3),
('00000000-0000-0000-0000-000000000001', 'MANUAL_VER', 'Verificación Manual Autorizada', true, 4)
ON CONFLICT (airline_id, code) DO NOTHING;
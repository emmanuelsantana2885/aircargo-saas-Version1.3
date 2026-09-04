-- Tabla de reportes del Dashboard Builder (reporte calculable tipo hoja de cálculo)
-- Se crea en el schema export_bi (spring.flyway.schemas=export_bi).
CREATE TABLE IF NOT EXISTS export_bi.dashboard_report (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL,
    name          VARCHAR(120) NOT NULL,
    field_sources JSONB NOT NULL DEFAULT '[]',
    formulas      JSONB NOT NULL DEFAULT '[]',
    scenario      JSONB NOT NULL DEFAULT '{}',
    grouping      JSONB NOT NULL DEFAULT '{}',
    chart_config  JSONB NOT NULL DEFAULT '{}',
    is_shared     BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dashboard_report_user ON export_bi.dashboard_report(user_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON export_bi.dashboard_report TO aircargo_user;

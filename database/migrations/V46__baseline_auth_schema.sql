-- ═══════════════════════════════════════════════════════════════
-- V20 — Baseline del esquema de auth-service.
--
-- Hasta hoy el esquema lo creaba Hibernate (ddl-auto=update) y Flyway
-- estaba deshabilitado; esta migración captura el estado REAL y es
-- 100% idempotente:
--   · BD existente  → todos los objetos ya existen → no-ops
--   · BD nueva      → crea el esquema completo desde cero
-- A partir de aquí, TODO cambio de esquema va por migración explícita
-- (ddl-auto=validate falla el arranque si hay drift).
-- ═══════════════════════════════════════════════════════════════

-- ── airline ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS airline (
    id UUID DEFAULT gen_random_uuid() NOT NULL,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    iata_code VARCHAR(3),
    country VARCHAR(60),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT airline_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS airline_code_key ON airline(code);

-- ── app_user ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app_user (
    id UUID NOT NULL,
    email VARCHAR(200) NOT NULL,
    full_name VARCHAR(150),
    role VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255),
    mfa_secret VARCHAR(255),
    mfa_enabled BOOLEAN NOT NULL DEFAULT false,
    mfa_locked BOOLEAN NOT NULL DEFAULT false,
    must_change_password BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    blocked BOOLEAN NOT NULL DEFAULT false,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login TIMESTAMPTZ,
    supabase_uid UUID,
    airline_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT app_user_pkey PRIMARY KEY (id),
    CONSTRAINT app_user_airline_fk FOREIGN KEY (airline_id) REFERENCES airline(id)
);
CREATE UNIQUE INDEX IF NOT EXISTS app_user_email_uk ON app_user(email);
CREATE INDEX IF NOT EXISTS idx_app_user_airline ON app_user(airline_id);

-- Columnas que deployments viejos pueden no tener aún (ddl-auto legacy)
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS blocked BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS supabase_uid UUID;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS mfa_secret VARCHAR(255);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS mfa_locked BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS last_login TIMESTAMPTZ;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'app_user')
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'app_user_role_check') THEN
        ALTER TABLE app_user ADD CONSTRAINT app_user_role_check CHECK (
            role IN ('READ_ONLY','WAREHOUSE_ASSISTANT','OPERATIONS','TRAFFIC',
                     'LOAD_PLANNER','ADMIN','SUPER_USER','BI_USER'));
    END IF;
END $$;

-- ── site ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS site (
    id UUID NOT NULL,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    country VARCHAR(60),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT site_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS site_code_uk ON site(code);

-- ── user_sites ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_sites (
    user_id UUID NOT NULL REFERENCES app_user(id),
    site_id UUID NOT NULL REFERENCES site(id),
    CONSTRAINT user_sites_pkey PRIMARY KEY (user_id, site_id)
);

-- ── view_permission ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS view_permission (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT view_permission_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS view_permission_code_uk ON view_permission(code);

-- ── role_permission ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS role_permission (
    id UUID NOT NULL,
    role VARCHAR(50) NOT NULL,
    can_access BOOLEAN NOT NULL DEFAULT true,
    view_permission_id UUID NOT NULL REFERENCES view_permission(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT role_permission_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS role_permission_uk ON role_permission(role, view_permission_id);

-- ── audit_log (legacy, congelado — lecturas via query-side) ──
CREATE TABLE IF NOT EXISTS audit_log (
    id UUID NOT NULL,
    user_id UUID,
    email VARCHAR(200),
    full_name VARCHAR(150),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(50),
    details TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT audit_log_pkey PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at);

-- ── audit_event (event store append-only) ─────────────────────
CREATE TABLE IF NOT EXISTS audit_event (
    id UUID PRIMARY KEY,
    user_id UUID,
    email VARCHAR(200),
    full_name VARCHAR(150),
    event_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(50),
    payload TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_event_user_id ON audit_event(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON audit_event(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_event_created_at ON audit_event(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_entity ON audit_event(entity_type, entity_id);

-- ── password_reset_token (enlaces un solo uso, 15 min) ───────
CREATE TABLE IF NOT EXISTS password_reset_token (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT password_reset_token_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_prt_token_hash ON password_reset_token(token_hash);
CREATE INDEX IF NOT EXISTS idx_prt_user ON password_reset_token(user_id);

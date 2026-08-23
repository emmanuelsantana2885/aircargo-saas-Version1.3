-- Audit event store (event sourcing): append-only facts, never updated/deleted.
-- NOTE: auth-service runs with spring.flyway.enabled=false + ddl-auto=update at
-- runtime; this file documents the schema and is applied by deployments that
-- run Flyway. Hibernate creates the table automatically otherwise.
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
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_event_user_id ON audit_event(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON audit_event(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_event_created_at ON audit_event(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_entity ON audit_event(entity_type, entity_id);

-- One-time backfill: carry legacy audit_log rows into the event store so the
-- query side keeps serving full history from a single source.
INSERT INTO audit_event (id, user_id, email, full_name, event_type, entity_type, entity_id, payload, ip_address, created_at)
SELECT id, user_id, email, full_name,
       CASE action
           WHEN 'LOGIN' THEN 'LOGIN_SUCCEEDED'
           WHEN 'CREATE' THEN 'USER_CREATED'
           WHEN 'UPDATE' THEN 'USER_UPDATED'
           WHEN 'DELETE' THEN 'USER_DELETED'
           ELSE action
       END,
       COALESCE(entity_type, 'USER'),
       COALESCE(entity_id, ''),
       details,
       ip_address,
       created_at
FROM audit_log l
WHERE NOT EXISTS (SELECT 1 FROM audit_event e WHERE e.id = l.id);

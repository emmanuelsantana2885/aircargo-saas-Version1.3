-- Sincronizado desde aircargo-uld-service V8__rename_built_by_to_loaded_by_add_weighed_by.sql
-- Renombrar built_by -> loaded_by y agregar weighed_by

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'uld' AND column_name = 'built_by') THEN
        ALTER TABLE uld RENAME COLUMN built_by TO loaded_by;
    END IF;
END $$;

ALTER TABLE uld
    ADD COLUMN IF NOT EXISTS weighed_by VARCHAR(100);

COMMENT ON COLUMN uld.loaded_by IS 'Operador que cargó el ULD (Loaded By)';
COMMENT ON COLUMN uld.weighed_by IS 'Operador que pesó el ULD (Weighed By)';
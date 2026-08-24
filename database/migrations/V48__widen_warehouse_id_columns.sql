-- Los campos de identidad se cifran con AES-256-GCM (prefijo "enc:v1:" + Base64),
-- lo que expande el texto ~1.8x. VARCHAR(50) ya no alcanza para una cédula cifrada.
ALTER TABLE warehouse_receipt
    ALTER COLUMN delivered_by_id_num TYPE VARCHAR(200),
    ALTER COLUMN received_by_id_num TYPE VARCHAR(200),
    ALTER COLUMN broker_id_num TYPE VARCHAR(200);

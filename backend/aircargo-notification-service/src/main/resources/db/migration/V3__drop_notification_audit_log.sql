-- Elimina la copia duplicada de auditoría en el schema notification.
--
-- Antes: los servicios publicaban "audit.log" por AMQP y este servicio
-- persistía una COPIA en notification.audit_log, mientras la escritura
-- primaria ya iba directa a la tabla compartida public.audit_log
-- (com.aircargo.common.audit.AuditService) → cada evento quedaba DOS veces.
--
-- El binding AMQP "audit.log" y su consumidor fueron eliminados; esta tabla
-- deja de tener propósito. La auditoría única y vigente vive en public.audit_log.

DROP TABLE IF EXISTS notification.audit_log;

package com.aircargo.notificationservice.config;

import com.aircargo.notificationservice.entity.AuditLog;
import com.aircargo.notificationservice.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Retención de la copia de auditoría que persiste este servicio
 * (schema notification.audit_log): elimina registros con más de
 * {@code app.audit.retention-months} meses (default 24). Diario 03:35.
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditLogRepository auditLogRepository;
    private final long retentionMonths;

    public AuditRetentionJob(AuditLogRepository auditLogRepository,
                             @Value("${app.audit.retention-months:24}") long retentionMonths) {
        this.auditLogRepository = auditLogRepository;
        this.retentionMonths = retentionMonths;
    }

    @Transactional
    public long purgeNow() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMonths(retentionMonths);
        return auditLogRepository.deleteByCreatedAtBefore(cutoff);
    }

    @Scheduled(cron = "0 35 3 * * *")
    public void purgeExpiredAudit() {
        long deleted = purgeNow();
        if (deleted > 0) {
            log.info("Retención de auditoría notification ({} meses): eliminados {} registros anteriores a la fecha de corte",
                    retentionMonths, deleted);
        }
    }
}

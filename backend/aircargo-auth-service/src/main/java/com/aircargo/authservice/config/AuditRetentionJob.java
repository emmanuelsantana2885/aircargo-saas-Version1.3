package com.aircargo.authservice.config;

import com.aircargo.authservice.event.AuditEventRepository;
import com.aircargo.authservice.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Retención de auditoría: elimina registros con más de
 * {@code app.audit.retention-months} (default 24) meses, conforme a la
 * Política de Privacidad publicada. Corre diario a las 03:30.
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditEventRepository auditEventRepository;
    private final AuditLogRepository legacyAuditLogRepository;
    private final long retentionMonths;

    public AuditRetentionJob(AuditEventRepository auditEventRepository,
                             AuditLogRepository legacyAuditLogRepository,
                             @Value("${app.audit.retention-months:24}") long retentionMonths) {
        this.auditEventRepository = auditEventRepository;
        this.legacyAuditLogRepository = legacyAuditLogRepository;
        this.retentionMonths = retentionMonths;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredAudit() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMonths(retentionMonths);
        long events = auditEventRepository.deleteByCreatedAtBefore(cutoff);
        long legacy = legacyAuditLogRepository.deleteByCreatedAtBefore(cutoff);
        if (events > 0 || legacy > 0) {
            log.info("Retención de auditoría ({} meses): eliminados {} eventos y {} registros legacy anteriores a {}",
                    retentionMonths, events, legacy, cutoff.toLocalDate());
        }
    }
}

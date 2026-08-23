package com.aircargo.notificationservice.repository;

import com.aircargo.notificationservice.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    long deleteByCreatedAtBefore(java.time.OffsetDateTime cutoff);
}

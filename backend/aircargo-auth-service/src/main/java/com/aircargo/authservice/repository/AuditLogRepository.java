package com.aircargo.authservice.repository;

import com.aircargo.authservice.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<AuditLog> findAllByOrderByCreatedAtDesc();
    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);

    @Query("SELECT a FROM AuditLog a WHERE a.action IN :actions OR a.entityType = :entityType ORDER BY a.createdAt DESC")
    List<AuditLog> findSecurityEvents(@Param("actions") List<String> actions, @Param("entityType") String entityType);
}

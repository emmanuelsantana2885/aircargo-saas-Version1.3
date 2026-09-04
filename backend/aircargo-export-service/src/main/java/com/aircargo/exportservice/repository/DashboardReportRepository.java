package com.aircargo.exportservice.repository;

import com.aircargo.exportservice.entity.DashboardReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DashboardReportRepository extends JpaRepository<DashboardReportEntity, UUID> {
    List<DashboardReportEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    List<DashboardReportEntity> findBySharedTrueOrderByUpdatedAtDesc();
    void deleteByIdAndUserId(UUID id, UUID userId);
}

package com.aircargo.authservice.repository;

import com.aircargo.authservice.entity.BackupHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BackupHistoryRepository extends JpaRepository<BackupHistory, java.util.UUID> {
    Page<BackupHistory> findByBackupType(String backupType, Pageable pageable);
    Page<BackupHistory> findByStatus(String status, Pageable pageable);
    List<BackupHistory> findByCreatedAtBetween(Instant start, Instant end);
    Page<BackupHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(String status);
    long countByBackupType(String backupType);
}
package com.aircargo.authservice.repository;

import com.aircargo.authservice.entity.BackupConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BackupConfigRepository extends JpaRepository<BackupConfig, Integer> {
}
package com.aircargo.authservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "backup_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BackupConfig {

    @Id
    @Column(name = "id")
    private Integer id = 1;

    @Column(name = "backup_dir", length = 500, nullable = false)
    private String backupDir;

    @Column(name = "keep_days", nullable = false)
    private Integer keepDays = 30;

    @Column(name = "compress_level", nullable = false)
    private Integer compressLevel = 6;

    @Column(name = "auto_backup_enabled", nullable = false)
    private Boolean autoBackupEnabled = true;

    @Column(name = "auto_backup_schedule", length = 50)
    private String autoBackupSchedule = "0 2 * * ?";

    @Column(name = "notify_on_success", nullable = false)
    private Boolean notifyOnSuccess = false;

    @Column(name = "notify_on_failure", nullable = false)
    private Boolean notifyOnFailure = true;

    @Column(name = "notification_emails", columnDefinition = "TEXT")
    private String notificationEmails;

    @Version
    private Long version;
}
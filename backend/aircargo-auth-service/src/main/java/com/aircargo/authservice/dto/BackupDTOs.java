package com.aircargo.authservice.dto;

import com.aircargo.authservice.entity.BackupConfig;
import com.aircargo.authservice.entity.BackupHistory;
import lombok.*;

import java.time.Instant;

public class BackupDTOs {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BackupConfigDTO {
        private Integer id;
        private String backupDir;
        private Integer keepDays;
        private Integer compressLevel;
        private Boolean autoBackupEnabled;
        private String autoBackupSchedule;
        private Boolean notifyOnSuccess;
        private Boolean notifyOnFailure;
        private String notificationEmails;

        public static BackupConfigDTO fromEntity(BackupConfig e) {
            if (e == null) return null;
            return BackupConfigDTO.builder()
                .id(e.getId())
                .backupDir(e.getBackupDir())
                .keepDays(e.getKeepDays())
                .compressLevel(e.getCompressLevel())
                .autoBackupEnabled(e.getAutoBackupEnabled())
                .autoBackupSchedule(e.getAutoBackupSchedule())
                .notifyOnSuccess(e.getNotifyOnSuccess())
                .notifyOnFailure(e.getNotifyOnFailure())
                .notificationEmails(e.getNotificationEmails())
                .build();
        }

        public BackupConfig toEntity() {
            return BackupConfig.builder()
                .id(id)
                .backupDir(backupDir)
                .keepDays(keepDays)
                .compressLevel(compressLevel)
                .autoBackupEnabled(autoBackupEnabled)
                .autoBackupSchedule(autoBackupSchedule)
                .notifyOnSuccess(notifyOnSuccess)
                .notifyOnFailure(notifyOnFailure)
                .notificationEmails(notificationEmails)
                .build();
        }
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BackupHistoryDTO {
        private java.util.UUID id;
        private String fileName;
        private String filePath;
        private Long sizeBytes;
        private String backupType;
        private String status;
        private String errorMessage;
        private Long durationMs;
        private Instant createdAt;
        private Instant completedAt;

        public static BackupHistoryDTO fromEntity(BackupHistory e) {
            if (e == null) return null;
            return BackupHistoryDTO.builder()
                .id(e.getId())
                .fileName(e.getFileName())
                .filePath(e.getFilePath())
                .sizeBytes(e.getSizeBytes())
                .backupType(e.getBackupType())
                .status(e.getStatus())
                .errorMessage(e.getErrorMessage())
                .durationMs(e.getDurationMs())
                .createdAt(e.getCreatedAt())
                .completedAt(e.getCompletedAt())
                .build();
        }
    }

    /** Petición de restauración de la BD. source = 'local' (filePath) o 'url' (url, http/https). */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RestoreRequest {
        private String source;      // "local" | "url"
        private String filePath;    // ruta del dump local (source=local)
        private String url;         // https://.../backup.dump (source=url)
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RestoreResult {
        private boolean success;
        private String message;
        private String dumpPath;
        private Integer exitCode;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BackupStatsDTO {
        private Long totalBackups;
        private Long totalSizeBytes;
        private Long successCount;
        private Long failureCount;
        private Long preDeployCount;
        private Long postDeployCount;
        private Long manualCount;
        private String oldestBackup;
        private String newestBackup;
        private String backupDir;
        private Long availableSpaceBytes;
    }
}
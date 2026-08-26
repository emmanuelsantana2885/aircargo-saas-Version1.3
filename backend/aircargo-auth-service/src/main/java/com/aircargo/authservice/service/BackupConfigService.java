package com.aircargo.authservice.service;

import com.aircargo.authservice.dto.BackupDTOs;
import com.aircargo.authservice.entity.BackupConfig;
import com.aircargo.authservice.entity.BackupHistory;
import com.aircargo.authservice.repository.BackupConfigRepository;
import com.aircargo.authservice.repository.BackupHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BackupConfigService {

    private final BackupConfigRepository configRepo;
    private final BackupHistoryRepository historyRepo;

    @Transactional(readOnly = true)
    public BackupDTOs.BackupConfigDTO getConfig() {
        BackupConfig config = configRepo.findById(1).orElse(null);
        if (config == null) {
            return defaultConfig();
        }
        // '' o ruta inexistente en BD → default del sistema ($HOME/aircargo-backups)
        if (config.getBackupDir() == null || config.getBackupDir().isBlank()) {
            config.setBackupDir(defaultDir());
        }
        return BackupDTOs.BackupConfigDTO.fromEntity(config);
    }

    @Transactional
    public BackupDTOs.BackupConfigDTO updateConfig(BackupDTOs.BackupConfigDTO dto) {
        if (dto.getBackupDir() == null || dto.getBackupDir().isBlank()) {
            throw new IllegalArgumentException("La carpeta de backups no puede estar vacía");
        }
        File dir = new File(dto.getBackupDir());
        if (!dir.isAbsolute()) {
            throw new IllegalArgumentException("La ruta debe ser absoluta (ej. /home/usuario/aircargo-backups)");
        }
        // Crear la carpeta si no existe (el admin puede apuntar a una nueva ubicación)
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalArgumentException("No se pudo crear la carpeta: " + dto.getBackupDir());
        }
        if (!dir.canWrite()) {
            throw new IllegalArgumentException("Sin permisos de escritura en: " + dto.getBackupDir());
        }
        BackupConfig config = configRepo.findById(1).orElseGet(() -> {
            BackupConfig c = new BackupConfig();
            c.setId(1);
            return c;
        });
        config.setBackupDir(dto.getBackupDir());
        if (dto.getKeepDays() != null) {
            if (dto.getKeepDays() < 1) throw new IllegalArgumentException("La retención debe ser al menos 1 día");
            config.setKeepDays(dto.getKeepDays());
        }
        if (dto.getCompressLevel() != null) {
            if (dto.getCompressLevel() < 0 || dto.getCompressLevel() > 9) {
                throw new IllegalArgumentException("La compresión debe estar entre 0 y 9");
            }
            config.setCompressLevel(dto.getCompressLevel());
        }
        if (dto.getAutoBackupEnabled() != null) config.setAutoBackupEnabled(dto.getAutoBackupEnabled());
        if (dto.getAutoBackupSchedule() != null && !dto.getAutoBackupSchedule().isBlank()) {
            config.setAutoBackupSchedule(dto.getAutoBackupSchedule());
        }
        if (dto.getNotifyOnSuccess() != null) config.setNotifyOnSuccess(dto.getNotifyOnSuccess());
        if (dto.getNotifyOnFailure() != null) config.setNotifyOnFailure(dto.getNotifyOnFailure());
        if (dto.getNotificationEmails() != null) config.setNotificationEmails(dto.getNotificationEmails());
        config = configRepo.save(config);
        return BackupDTOs.BackupConfigDTO.fromEntity(config);
    }

    @Transactional(readOnly = true)
    public BackupDTOs.BackupStatsDTO getStats() {
        BackupDTOs.BackupConfigDTO config = getConfig();
        File dir = new File(config.getBackupDir());
        List<BackupHistory> all = historyRepo.findAll();

        BackupDTOs.BackupStatsDTO stats = BackupDTOs.BackupStatsDTO.builder()
            .totalBackups((long) all.size())
            .totalSizeBytes(all.stream().mapToLong(BackupHistory::getSizeBytes).sum())
            .successCount(all.stream().filter(h -> "SUCCESS".equals(h.getStatus())).count())
            .failureCount(all.stream().filter(h -> "FAILED".equals(h.getStatus())).count())
            .preDeployCount(all.stream().filter(h -> "PRE_DEPLOY".equals(h.getBackupType())).count())
            .postDeployCount(all.stream().filter(h -> "POST_DEPLOY".equals(h.getBackupType())).count())
            .manualCount(all.stream().filter(h -> "MANUAL".equals(h.getBackupType())).count())
            .backupDir(config.getBackupDir())
            .availableSpaceBytes(dir.exists() ? dir.getUsableSpace() : 0L)
            .build();

        Optional<BackupHistory> oldest = all.stream()
            .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
        Optional<BackupHistory> newest = all.stream()
            .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));

        oldest.ifPresent(h -> stats.setOldestBackup(h.getCreatedAt().toString()));
        newest.ifPresent(h -> stats.setNewestBackup(h.getCreatedAt().toString()));

        return stats;
    }

    @Transactional(readOnly = true)
    public List<BackupDTOs.BackupHistoryDTO> getHistory(int page, int size) {
        return historyRepo.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(page, size))
            .map(BackupDTOs.BackupHistoryDTO::fromEntity)
            .getContent();
    }

    @Transactional(readOnly = true)
    public Optional<BackupDTOs.BackupHistoryDTO> getLatestBackup() {
        return historyRepo.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, 1))
            .getContent().stream()
            .findFirst()
            .map(BackupDTOs.BackupHistoryDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public Optional<BackupDTOs.BackupHistoryDTO> getLatestPreDeployBackup() {
        return historyRepo.findByBackupType("PRE_DEPLOY",
                org.springframework.data.domain.PageRequest.of(0, 1))
            .getContent().stream()
            .findFirst()
            .map(BackupDTOs.BackupHistoryDTO::fromEntity);
    }

    private String defaultDir() {
        return System.getProperty("user.home") + "/aircargo-backups";
    }

    private BackupDTOs.BackupConfigDTO defaultConfig() {
        return BackupDTOs.BackupConfigDTO.builder()
            .id(1)
            .backupDir(defaultDir())
            .keepDays(30)
            .compressLevel(6)
            .autoBackupEnabled(true)
            .autoBackupSchedule("0 2 * * ?")
            .notifyOnSuccess(false)
            .notifyOnFailure(true)
            .notificationEmails("")
            .build();
    }
}
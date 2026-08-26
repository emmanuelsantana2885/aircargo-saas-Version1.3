package com.aircargo.authservice.service;

import com.aircargo.authservice.dto.BackupDTOs;
import com.aircargo.authservice.entity.BackupConfig;
import com.aircargo.authservice.repository.BackupConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del servicio de configuración de backups:
 * resolución de carpeta por defecto, validaciones de update y merge null-safe.
 */
@ExtendWith(MockitoExtension.class)
class BackupConfigServiceTest {

    @Mock
    private BackupConfigRepository configRepo;

    private BackupConfigService service;

    @BeforeEach
    void setUp() {
        service = new BackupConfigService(configRepo, null);
    }

    private BackupConfig entity(String dir) {
        return BackupConfig.builder()
                .id(1).backupDir(dir).keepDays(30).compressLevel(6)
                .autoBackupEnabled(true).autoBackupSchedule("0 2 * * ?")
                .notifyOnSuccess(false).notifyOnFailure(true)
                .build();
    }

    @Test
    void getConfig_resuelveCadenaVaciaACarpetaDefaultDelSistema() {
        when(configRepo.findById(1)).thenReturn(Optional.of(entity("")));
        BackupDTOs.BackupConfigDTO dto = service.getConfig();
        assertTrue(dto.getBackupDir().endsWith("/aircargo-backups"),
                "'' debe resolverse a $HOME/aircargo-backups, fue: " + dto.getBackupDir());
    }

    @Test
    void getConfig_sinFilaEnBd_devuelveDefaults() {
        when(configRepo.findById(1)).thenReturn(Optional.empty());
        BackupDTOs.BackupConfigDTO dto = service.getConfig();
        assertEquals(30, dto.getKeepDays());
        assertEquals(6, dto.getCompressLevel());
        assertNotNull(dto.getBackupDir());
    }

    @Test
    void updateConfig_rutaRelativa_rechazada() {
        BackupDTOs.BackupConfigDTO dto = BackupDTOs.BackupConfigDTO.builder()
                .backupDir("carpeta-relativa").build();
        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(dto));
    }

    @Test
    void updateConfig_carpetaVacia_rechazada() {
        BackupDTOs.BackupConfigDTO dto = BackupDTOs.BackupConfigDTO.builder()
                .backupDir("   ").build();
        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(dto));
    }

    @Test
    void updateConfig_creaLaCarpetaSiNoExiste() {
        java.io.File nueva = new java.io.File("/tmp/aircargo-test-backup-" + System.nanoTime());
        try {
            assertFalse(nueva.exists());
            when(configRepo.findById(1)).thenReturn(Optional.of(entity("")));
            BackupDTOs.BackupConfigDTO dto = BackupDTOs.BackupConfigDTO.builder()
                    .backupDir(nueva.getAbsolutePath()).build();
            service.updateConfig(dto);
            assertTrue(nueva.isDirectory(), "La carpeta debe crearse automáticamente");
            ArgumentCaptor<BackupConfig> captor = ArgumentCaptor.forClass(BackupConfig.class);
            verify(configRepo).save(captor.capture());
            assertEquals(nueva.getAbsolutePath(), captor.getValue().getBackupDir());
        } finally {
            nueva.delete();
        }
    }

    @Test
    void updateConfig_mergeNullSafe_noPisaCamposNoEnviados() {
        String tmpDir = "/tmp/aircargo-test-backup-" + System.nanoTime();
        when(configRepo.findById(1)).thenReturn(Optional.of(entity(tmpDir)));
        when(configRepo.save(any(BackupConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        // Solo envía backupDir — keepDays/compressLevel deben conservarse
        BackupDTOs.BackupConfigDTO dto = BackupDTOs.BackupConfigDTO.builder()
                .backupDir(tmpDir).build();
        BackupDTOs.BackupConfigDTO saved = service.updateConfig(dto);

        assertEquals(30, saved.getKeepDays());
        assertEquals(6, saved.getCompressLevel());
        verify(configRepo).save(any(BackupConfig.class));
    }

    @Test
    void updateConfig_compresionFueraDeRango_rechazada() {
        String tmpDir = "/tmp/aircargo-test-backup-" + System.nanoTime();
        when(configRepo.findById(1)).thenReturn(Optional.of(entity(tmpDir)));
        BackupDTOs.BackupConfigDTO dto = BackupDTOs.BackupConfigDTO.builder()
                .backupDir(tmpDir).compressLevel(11).build();
        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(dto));
    }
}

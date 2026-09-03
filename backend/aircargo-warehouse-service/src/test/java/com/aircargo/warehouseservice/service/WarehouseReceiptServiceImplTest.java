package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.dto.WarehouseReceiptDTO;
import com.aircargo.warehouseservice.entity.WarehouseReceipt;
import com.aircargo.warehouseservice.repository.WarehouseReceiptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseReceiptServiceImplTest {

    @Mock
    private WarehouseReceiptRepository receiptRepository;

    @InjectMocks
    private WarehouseReceiptServiceImpl service;

    private WarehouseReceipt sampleReceipt() {
        WarehouseReceipt r = new WarehouseReceipt();
        r.setId(UUID.randomUUID());
        r.setMawbNumber("406-05912970");
        r.setSuperseded(false);
        return r;
    }

    @Test
    void getAll_excludesSupersededReceipts() {
        when(receiptRepository.findBySupersededFalse()).thenReturn(List.of(sampleReceipt(), sampleReceipt()));

        List<WarehouseReceiptDTO> result = service.getAll();

        assertEquals(2, result.size());
        verify(receiptRepository).findBySupersededFalse();
        verify(receiptRepository, never()).findAll();
    }

    @Test
    void getById_mapsEntity() {
        WarehouseReceipt r = sampleReceipt();
        when(receiptRepository.findById(r.getId())).thenReturn(Optional.of(r));

        Optional<WarehouseReceiptDTO> result = service.getById(r.getId());

        assertTrue(result.isPresent());
        assertEquals("406-05912970", result.get().getMawbNumber());
    }

    @Test
    void save_alwaysMarksAsNonSuperseded() {
        WarehouseReceiptDTO dto = new WarehouseReceiptDTO();
        dto.setMawbNumber("406-05912970");
        dto.setSuperseded(true);

        WarehouseReceipt saved = sampleReceipt();
        when(receiptRepository.save(any(WarehouseReceipt.class))).thenReturn(saved);

        service.save(dto);

        ArgumentCaptor<WarehouseReceipt> captor = ArgumentCaptor.forClass(WarehouseReceipt.class);
        verify(receiptRepository).save(captor.capture());
        assertFalse(captor.getValue().getSuperseded());
    }

    @Test
    void delete_delegatesToRepository() {
        UUID id = UUID.randomUUID();

        service.delete(id);

        verify(receiptRepository).deleteById(id);
    }

    @Test
    void updateReceipt_mustBeTransactional() throws Exception {
        // Guard de regresión: updateReceipt borra piezas con deleteByReceiptId
        // (una operación de borrado derivada de Spring Data) que REQUIERE una
        // transacción activa. Sin @Transactional el runtime lanza
        // TransactionRequiredException (500 en PUT /api/warehouse/receipts/{id}).
        Method m = WarehouseServiceImpl.class.getMethod(
                "updateReceipt",
                UUID.class, WarehouseReceiptDTO.class,
                com.aircargo.common.auth.UserPrincipal.class,
                jakarta.servlet.http.HttpServletRequest.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx, "updateReceipt debe ser @Transactional para permitir deleteByReceiptId");
    }
}

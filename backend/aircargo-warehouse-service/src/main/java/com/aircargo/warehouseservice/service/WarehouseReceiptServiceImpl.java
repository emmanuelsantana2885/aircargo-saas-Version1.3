package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.dto.WarehouseReceiptDTO;
import com.aircargo.warehouseservice.entity.WarehouseReceipt;
import com.aircargo.warehouseservice.repository.WarehouseReceiptRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WarehouseReceiptServiceImpl implements WarehouseReceiptService {

    private final WarehouseReceiptRepository receiptRepository;

    public WarehouseReceiptServiceImpl(WarehouseReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
    }

    @Override
    @Cacheable("warehouse-receipts")
    public List<WarehouseReceiptDTO> getAll() {
        return receiptRepository.findBySupersededFalse().stream()
                .map(WarehouseReceiptDTO::fromEntity)
                .toList();
    }

    @Override
    @Cacheable(value = "warehouse-receipts", key = "#id")
    public Optional<WarehouseReceiptDTO> getById(UUID id) {
        return receiptRepository.findById(id).map(WarehouseReceiptDTO::fromEntity);
    }

    @Override
    @CacheEvict(value = {"warehouse-receipts", "receipt-pieces"}, allEntries = true)
    public WarehouseReceiptDTO save(WarehouseReceiptDTO dto) {
        WarehouseReceipt entity = WarehouseReceiptDTO.toEntity(dto);
        entity.setSuperseded(false);
        WarehouseReceipt saved = receiptRepository.save(entity);
        return WarehouseReceiptDTO.fromEntity(saved);
    }

    @Override
    @CacheEvict(value = {"warehouse-receipts", "receipt-pieces"}, allEntries = true)
    public void delete(UUID id) {
        receiptRepository.deleteById(id);
    }
}
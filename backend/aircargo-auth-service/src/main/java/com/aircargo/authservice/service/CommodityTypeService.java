package com.aircargo.authservice.service;

import com.aircargo.authservice.dto.CommodityTypeDTO;
import com.aircargo.authservice.entity.CommodityTypeEntity;
import com.aircargo.authservice.repository.CommodityTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CommodityTypeService {

    private final CommodityTypeRepository repository;

    public CommodityTypeService(CommodityTypeRepository repository) {
        this.repository = repository;
    }

    public List<CommodityTypeDTO> getAll(boolean activeOnly) {
        List<CommodityTypeEntity> list = activeOnly
            ? repository.findByIsActiveTrueOrderBySortOrderAscCodeAsc()
            : repository.findAllByOrderBySortOrderAscCodeAsc();
        return list.stream().map(CommodityTypeDTO::fromEntity).toList();
    }

    public CommodityTypeDTO getById(UUID id) {
        CommodityTypeEntity entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Commodity type not found: " + id));
        return CommodityTypeDTO.fromEntity(entity);
    }

    public CommodityTypeDTO getByCode(String code) {
        CommodityTypeEntity entity = repository.findByCodeIgnoreCase(code)
            .orElseThrow(() -> new IllegalArgumentException("Commodity type not found: " + code));
        return CommodityTypeDTO.fromEntity(entity);
    }

    @Transactional
    public CommodityTypeDTO create(String code, String label, String description, String color, Integer sortOrder, Boolean isActive) {
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Commodity type code already exists: " + code);
        }
        CommodityTypeEntity entity = CommodityTypeEntity.builder()
            .code(code.toUpperCase().trim())
            .label(label != null ? label.trim() : code.toUpperCase().trim())
            .description(description != null ? description.trim() : null)
            .color(color)
            .sortOrder(sortOrder != null ? sortOrder : 0)
            .isActive(isActive != null ? isActive : true)
            .build();
        return CommodityTypeDTO.fromEntity(repository.save(entity));
    }

    @Transactional
    public CommodityTypeDTO update(UUID id, String code, String label, String description, String color, Integer sortOrder, Boolean isActive) {
        CommodityTypeEntity entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Commodity type not found: " + id));

        if (code != null) {
            String upperCode = code.toUpperCase().trim();
            if (!upperCode.equalsIgnoreCase(entity.getCode()) && repository.existsByCodeIgnoreCase(upperCode)) {
                throw new IllegalArgumentException("Commodity type code already exists: " + upperCode);
            }
            entity.setCode(upperCode);
        }
        if (label != null) entity.setLabel(label.trim());
        if (description != null) entity.setDescription(description.trim());
        if (color != null) entity.setColor(color);
        if (sortOrder != null) entity.setSortOrder(sortOrder);
        if (isActive != null) entity.setIsActive(isActive);

        return CommodityTypeDTO.fromEntity(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Commodity type not found: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional
    public int resetToDefaults() {
        repository.deleteAll();
        List<CommodityTypeEntity> defaults = List.of(
            buildDefault("DRY_CARGO", "Dry Cargo", "General dry cargo", "#6366f1", 1),
            buildDefault("PERISHABLE", "Perishable", "Temperature-sensitive goods", "#10b981", 2),
            buildDefault("HAZMAT", "Hazmat", "Hazardous materials", "#ef4444", 3),
            buildDefault("FRAGILE", "Fragile", "Fragile items requiring special handling", "#f59e0b", 4),
            buildDefault("LIVE_ANIMALS", "Live Animals", "Live animal transport", "#8b5cf6", 5),
            buildDefault("VALUABLES", "Valuables", "High-value items", "#ec4899", 6),
            buildDefault("MEDICAL", "Medical", "Medical supplies and equipment", "#06b6d4", 7),
            buildDefault("OVERSIZED", "Oversized", "Oversized cargo", "#f97316", 8),
            buildDefault("MILITARY", "Military", "Military cargo", "#374151", 9),
            buildDefault("TEXTILES", "Textiles", "Textile products", "#a855f7", 10),
            buildDefault("ELECTRONICS", "Electronics", "Electronic devices and components", "#3b82f6", 11),
            buildDefault("MACHINERY", "Machinery", "Industrial machinery", "#64748b", 12),
            buildDefault("FOOD", "Food Products", "Non-perishable food items", "#22c55e", 13),
            buildDefault("CONSTRUCTION", "Construction", "Construction materials", "#78716c", 14),
            buildDefault("AUTOMOTIVE", "Automotive", "Auto parts and vehicles", "#dc2626", 15),
            buildDefault("CHEMICALS", "Chemicals", "Chemical products", "#0ea5e9", 16),
            buildDefault("GENERAL", "General", "General cargo", "#94a3b8", 17),
            buildDefault("OTHER", "Other", "Other cargo types", "#6b7280", 18),
            buildDefault("SDQ_SDF", "SDQ→SDF", "Domestic route SDQ to SDF", "#14b8a6", 19),
            buildDefault("SDQ_MIA", "SDQ→MIA", "Domestic route SDQ to MIA", "#0891b2", 20),
            buildDefault("WWEF", "WWEF", "Worldwide Express Freight", "#7c3aed", 21),
            buildDefault("FCC", "FCC", "Full Container Load", "#b91c1c", 22),
            buildDefault("EMPTY_ULD", "Empty ULD", "Empty ULD equipment", "#9ca3af", 23),
            buildDefault("EMPTY_BAGS", "Empty Bags", "Empty bags equipment", "#d1d5db", 24),
            buildDefault("NETS", "Nets", "Cargo nets", "#a3a3a3", 25)
        );
        repository.saveAll(defaults);
        return defaults.size();
    }

    private CommodityTypeEntity buildDefault(String code, String label, String description, String color, int order) {
        return CommodityTypeEntity.builder()
            .code(code).label(label).description(description)
            .color(color).sortOrder(order).isActive(true).build();
    }
}

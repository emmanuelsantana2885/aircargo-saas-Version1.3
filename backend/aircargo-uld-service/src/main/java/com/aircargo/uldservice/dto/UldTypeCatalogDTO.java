package com.aircargo.uldservice.dto;

import com.aircargo.uldservice.entity.UldTypeCatalog;

import java.time.OffsetDateTime;
import java.util.UUID;

public class UldTypeCatalogDTO {

    private UUID id;
    private String code;
    private String description;
    private Boolean isActive;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static UldTypeCatalogDTO fromEntity(UldTypeCatalog e) {
        if (e == null) return null;
        UldTypeCatalogDTO dto = new UldTypeCatalogDTO();
        dto.setId(e.getId());
        dto.setCode(e.getCode());
        dto.setDescription(e.getDescription());
        dto.setIsActive(e.getIsActive());
        dto.setSortOrder(e.getSortOrder());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    public static UldTypeCatalog toEntity(UldTypeCatalogDTO dto) {
        if (dto == null) return null;
        UldTypeCatalog e = new UldTypeCatalog();
        e.setId(dto.getId());
        e.setCode(dto.getCode());
        e.setDescription(dto.getDescription());
        e.setIsActive(dto.getIsActive() == null || dto.getIsActive());
        e.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        return e;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

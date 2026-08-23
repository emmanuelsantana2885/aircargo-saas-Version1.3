package com.aircargo.uldservice.dto;

import com.aircargo.uldservice.entity.UldTypeConfig;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class UldTypeConfigDTO {

    private UUID id;
    private UUID airlineId;
    private String uldType;
    private BigDecimal defaultTareLbs;
    private BigDecimal maxGrossLbs;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static UldTypeConfigDTO fromEntity(UldTypeConfig c) {
        if (c == null) return null;
        UldTypeConfigDTO dto = new UldTypeConfigDTO();
        dto.setId(c.getId());
        dto.setAirlineId(c.getAirlineId());
        dto.setUldType(c.getUldType());
        dto.setDefaultTareLbs(c.getDefaultTareLbs());
        dto.setMaxGrossLbs(c.getMaxGrossLbs());
        dto.setNotes(c.getNotes());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        return dto;
    }

    public static UldTypeConfig toEntity(UldTypeConfigDTO dto) {
        if (dto == null) return null;
        UldTypeConfig c = new UldTypeConfig();
        c.setId(dto.getId());
        c.setAirlineId(dto.getAirlineId());
        c.setUldType(dto.getUldType());
        c.setDefaultTareLbs(dto.getDefaultTareLbs());
        c.setMaxGrossLbs(dto.getMaxGrossLbs());
        c.setNotes(dto.getNotes());
        return c;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAirlineId() { return airlineId; }
    public void setAirlineId(UUID airlineId) { this.airlineId = airlineId; }
    public String getUldType() { return uldType; }
    public void setUldType(String uldType) { this.uldType = uldType == null ? null : uldType.trim().toUpperCase(); }
    public BigDecimal getDefaultTareLbs() { return defaultTareLbs; }
    public void setDefaultTareLbs(BigDecimal defaultTareLbs) { this.defaultTareLbs = defaultTareLbs; }
    public BigDecimal getMaxGrossLbs() { return maxGrossLbs; }
    public void setMaxGrossLbs(BigDecimal maxGrossLbs) { this.maxGrossLbs = maxGrossLbs; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

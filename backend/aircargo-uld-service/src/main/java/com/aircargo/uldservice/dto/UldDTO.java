package com.aircargo.uldservice.dto;

import com.aircargo.uldservice.entity.Uld;
import com.aircargo.uldservice.entity.UldStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class UldDTO {

    private UUID id;
    private UUID airlineId;
    private UUID flightId;
    private String uldNumber;
    private String uldType;
    private String position;
    private String config;
    private String sealNumber;
    private BigDecimal tareLbs;
    private BigDecimal grossWeightLbs;
    private BigDecimal netWeightLbs;
    private BigDecimal tareKg;
    private BigDecimal grossWeightKg;
    private BigDecimal netWeightKg;
    private UldStatus status;
    private OffsetDateTime builtAt;
    private OffsetDateTime loadedAt;
    private String notes;
    private String destination;
    private String builtBy;
    private String confirmedWith;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<UldAwbDTO> awbs;

    public UldDTO() {}

    public static UldDTO fromEntity(Uld entity) {
        if (entity == null) return null;
        UldDTO dto = new UldDTO();
        dto.setId(entity.getId());
        dto.setAirlineId(entity.getAirlineId());
        dto.setFlightId(entity.getFlightId());
        dto.setUldNumber(entity.getUldNumber());
        dto.setUldType(entity.getUldType());
        dto.setPosition(entity.getPosition());
        dto.setConfig(entity.getConfig());
        dto.setSealNumber(entity.getSealNumber());
        dto.setTareLbs(entity.getTareLbs());
        dto.setGrossWeightLbs(entity.getGrossWeightLbs());
        dto.setNetWeightLbs(entity.getNetWeightLbs());
        dto.setTareKg(entity.getTareKg());
        dto.setGrossWeightKg(entity.getGrossWeightKg());
        dto.setNetWeightKg(entity.getNetWeightKg());
        dto.setStatus(entity.getStatus());
        dto.setBuiltAt(entity.getBuiltAt());
        dto.setLoadedAt(entity.getLoadedAt());
        dto.setNotes(entity.getNotes());
        dto.setDestination(entity.getDestination());
        dto.setBuiltBy(entity.getBuiltBy());
        dto.setConfirmedWith(entity.getConfirmedWith());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public static Uld toEntity(UldDTO dto) {
        if (dto == null) return null;
        Uld entity = new Uld();
        entity.setId(dto.getId());
        entity.setAirlineId(dto.getAirlineId());
        entity.setFlightId(dto.getFlightId());
        entity.setUldNumber(dto.getUldNumber());
        entity.setUldType(dto.getUldType());
        entity.setPosition(dto.getPosition());
        entity.setConfig(dto.getConfig());
        entity.setSealNumber(dto.getSealNumber());
        entity.setTareLbs(dto.getTareLbs());
        entity.setGrossWeightLbs(dto.getGrossWeightLbs());
        entity.setNetWeightLbs(dto.getNetWeightLbs());
        entity.setTareKg(dto.getTareKg());
        entity.setGrossWeightKg(dto.getGrossWeightKg());
        entity.setNetWeightKg(dto.getNetWeightKg());
        entity.setStatus(dto.getStatus());
        entity.setBuiltAt(dto.getBuiltAt());
        entity.setLoadedAt(dto.getLoadedAt());
        entity.setNotes(dto.getNotes());
        entity.setDestination(dto.getDestination());
        entity.setBuiltBy(dto.getBuiltBy());
        entity.setConfirmedWith(dto.getConfirmedWith());
        entity.setCompletedAt(dto.getCompletedAt());
        return entity;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAirlineId() { return airlineId; }
    public void setAirlineId(UUID airlineId) { this.airlineId = airlineId; }
    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
    public String getUldNumber() { return uldNumber; }
    public void setUldNumber(String uldNumber) { this.uldNumber = uldNumber; }
    public String getUldType() { return uldType; }
    public void setUldType(String uldType) { this.uldType = uldType == null ? null : uldType.trim().toUpperCase(); }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public String getSealNumber() { return sealNumber; }
    public void setSealNumber(String sealNumber) { this.sealNumber = sealNumber; }
    public BigDecimal getTareLbs() { return tareLbs; }
    public void setTareLbs(BigDecimal tareLbs) { this.tareLbs = tareLbs; }
    public BigDecimal getGrossWeightLbs() { return grossWeightLbs; }
    public void setGrossWeightLbs(BigDecimal grossWeightLbs) { this.grossWeightLbs = grossWeightLbs; }
    public BigDecimal getNetWeightLbs() { return netWeightLbs; }
    public void setNetWeightLbs(BigDecimal netWeightLbs) { this.netWeightLbs = netWeightLbs; }
    public BigDecimal getTareKg() { return tareKg; }
    public void setTareKg(BigDecimal tareKg) { this.tareKg = tareKg; }
    public BigDecimal getGrossWeightKg() { return grossWeightKg; }
    public void setGrossWeightKg(BigDecimal grossWeightKg) { this.grossWeightKg = grossWeightKg; }
    public BigDecimal getNetWeightKg() { return netWeightKg; }
    public void setNetWeightKg(BigDecimal netWeightKg) { this.netWeightKg = netWeightKg; }
    public UldStatus getStatus() { return status; }
    public void setStatus(UldStatus status) { this.status = status; }
    public OffsetDateTime getBuiltAt() { return builtAt; }
    public void setBuiltAt(OffsetDateTime builtAt) { this.builtAt = builtAt; }
    public OffsetDateTime getLoadedAt() { return loadedAt; }
    public void setLoadedAt(OffsetDateTime loadedAt) { this.loadedAt = loadedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getBuiltBy() { return builtBy; }
    public void setBuiltBy(String builtBy) { this.builtBy = builtBy; }
    public String getConfirmedWith() { return confirmedWith; }
    public void setConfirmedWith(String confirmedWith) { this.confirmedWith = confirmedWith; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<UldAwbDTO> getAwbs() { return awbs; }
    public void setAwbs(List<UldAwbDTO> awbs) { this.awbs = awbs; }
}

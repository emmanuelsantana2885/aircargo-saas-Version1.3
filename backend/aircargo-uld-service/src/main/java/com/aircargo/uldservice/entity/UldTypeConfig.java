package com.aircargo.uldservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "uld_type_config")
public class UldTypeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "airline_id", nullable = false)
    private UUID airlineId;

    @Column(name = "uld_type", nullable = false, length = 10)
    private String uldType;

    @Column(name = "default_tare_lbs", nullable = false, precision = 8, scale = 2)
    private BigDecimal defaultTareLbs;

    @Column(name = "max_gross_lbs", precision = 10, scale = 2)
    private BigDecimal maxGrossLbs;

    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UldTypeConfig() {}

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

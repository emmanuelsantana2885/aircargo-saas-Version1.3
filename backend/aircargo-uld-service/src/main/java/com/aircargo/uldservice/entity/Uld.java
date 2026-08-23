package com.aircargo.uldservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "uld")
public class Uld {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "airline_id", nullable = false)
    private UUID airlineId;

    @Column(name = "flight_id")
    private UUID flightId;

    @Column(name = "uld_number", nullable = false, length = 30)
    private String uldNumber;

    @Column(name = "uld_type", nullable = false, length = 10)
    private String uldType;

    @Column(name = "position", length = 10)
    private String position;

    @Column(name = "config", length = 10)
    private String config;

    @Column(name = "seal_number", length = 50)
    private String sealNumber;

    @Column(name = "tare_lbs", nullable = false, precision = 8, scale = 2)
    private BigDecimal tareLbs = BigDecimal.ZERO;

    @Column(name = "tare_notes", length = 200)
    private String tareNotes;

    @Column(name = "gross_weight_lbs", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossWeightLbs = BigDecimal.ZERO;

    @Column(name = "net_weight_lbs")
    private BigDecimal netWeightLbs;

    @Column(name = "tare_kg")
    private BigDecimal tareKg;

    @Column(name = "gross_weight_kg")
    private BigDecimal grossWeightKg;

    @Column(name = "net_weight_kg")
    private BigDecimal netWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UldStatus status = UldStatus.OPEN;

    @Column(name = "built_at")
    private OffsetDateTime builtAt;

    @Column(name = "loaded_at")
    private OffsetDateTime loadedAt;

    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Uld() {}

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
    public String getTareNotes() { return tareNotes; }
    public void setTareNotes(String tareNotes) { this.tareNotes = tareNotes; }
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
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

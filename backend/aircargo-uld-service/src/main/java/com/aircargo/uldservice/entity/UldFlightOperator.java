package com.aircargo.uldservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Operador por (ULD, vuelo): cada vuelo en el que se usa un ULD conserva su propia
 * snapshot de Loaded By / Weighed By / Confirmed With, independiente de la asignación
 * actual del ULD. Las columnas de uld se mantienen como estado corriente.
 */
@Entity
@Table(name = "uld_flight_operator")
public class UldFlightOperator {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "uld_id", nullable = false)
    private UUID uldId;

    @Column(name = "flight_id", nullable = false)
    private UUID flightId;

    @Column(name = "loaded_by", length = 100)
    private String loadedBy;

    @Column(name = "weighed_by", length = 100)
    private String weighedBy;

    @Column(name = "confirmed_with", length = 100)
    private String confirmedWith;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UldFlightOperator() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUldId() { return uldId; }
    public void setUldId(UUID uldId) { this.uldId = uldId; }
    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
    public String getLoadedBy() { return loadedBy; }
    public void setLoadedBy(String loadedBy) { this.loadedBy = loadedBy; }
    public String getWeighedBy() { return weighedBy; }
    public void setWeighedBy(String weighedBy) { this.weighedBy = weighedBy; }
    public String getConfirmedWith() { return confirmedWith; }
    public void setConfirmedWith(String confirmedWith) { this.confirmedWith = confirmedWith; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
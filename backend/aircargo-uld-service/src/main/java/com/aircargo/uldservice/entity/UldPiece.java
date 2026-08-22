package com.aircargo.uldservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "uld_piece")
public class UldPiece {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "uld_id", nullable = false)
    private UUID uldId;

    @Column(name = "mawb_id")
    private UUID mawbId;

    @Column(name = "awb_number", length = 20)
    private String awbNumber;

    @Column(name = "hawb_number", length = 30)
    private String hawbNumber;

    @Column(name = "piece_number", nullable = false)
    private Integer pieceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private PieceSource source = PieceSource.MANUAL;

    @Column(name = "scanned_by")
    private UUID scannedBy;

    @Column(name = "scanned_at")
    private OffsetDateTime scannedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public UldPiece() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUldId() { return uldId; }
    public void setUldId(UUID uldId) { this.uldId = uldId; }
    public UUID getMawbId() { return mawbId; }
    public void setMawbId(UUID mawbId) { this.mawbId = mawbId; }
    public String getAwbNumber() { return awbNumber; }
    public void setAwbNumber(String awbNumber) { this.awbNumber = awbNumber; }
    public String getHawbNumber() { return hawbNumber; }
    public void setHawbNumber(String hawbNumber) { this.hawbNumber = hawbNumber; }
    public Integer getPieceNumber() { return pieceNumber; }
    public void setPieceNumber(Integer pieceNumber) { this.pieceNumber = pieceNumber; }
    public PieceSource getSource() { return source; }
    public void setSource(PieceSource source) { this.source = source; }
    public UUID getScannedBy() { return scannedBy; }
    public void setScannedBy(UUID scannedBy) { this.scannedBy = scannedBy; }
    public OffsetDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(OffsetDateTime scannedAt) { this.scannedAt = scannedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

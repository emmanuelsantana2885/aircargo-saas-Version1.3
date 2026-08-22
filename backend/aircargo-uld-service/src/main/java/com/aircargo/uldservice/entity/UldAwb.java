package com.aircargo.uldservice.entity;

import com.aircargo.common.entity.CommodityType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "uld_awb")
public class UldAwb {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "uld_id", nullable = false)
    private UUID uldId;

    @Column(name = "mawb_id")
    private UUID mawbId;

    @Column(name = "mawb_label", length = 50)
    private String mawbLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "description", nullable = false)
    private CommodityType description = CommodityType.DRY_CARGO;

    @Column(name = "destination", columnDefinition = "bpchar(3)")
    private String destination;

    @Column(name = "pieces")
    private Integer pieces = 0;

    @Column(name = "pieces_pct")
    private Integer piecesPct = 100;

    @Column(name = "temp_inbound", precision = 6, scale = 2)
    private BigDecimal tempInbound;

    @Column(name = "temp_outbound", precision = 6, scale = 2)
    private BigDecimal tempOutbound;

    @Column(name = "hc")
    private Boolean hc = false;

    @Column(name = "comments")
    private String comments;

    @Column(name = "consumption_pallets", precision = 6, scale = 3)
    private BigDecimal consumptionPallets;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "avg_time_per_piece_sec")
    private Integer avgTimePerPieceSec = 5;

    @Column(name = "lapse_minutes", insertable = false, updatable = false)
    private BigDecimal lapseMinutes;

    @Column(name = "pcs_per_min", insertable = false, updatable = false)
    private BigDecimal pcsPerMin;

    @Column(name = "operative_worked_hours", insertable = false, updatable = false)
    private BigDecimal operativeWorkedHours;

    @Column(name = "earned_hours", insertable = false, updatable = false)
    private BigDecimal earnedHours;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public UldAwb() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUldId() { return uldId; }
    public void setUldId(UUID uldId) { this.uldId = uldId; }
    public UUID getMawbId() { return mawbId; }
    public void setMawbId(UUID mawbId) { this.mawbId = mawbId; }
    public String getMawbLabel() { return mawbLabel; }
    public void setMawbLabel(String mawbLabel) { this.mawbLabel = mawbLabel; }
    public CommodityType getDescription() { return description; }
    public void setDescription(CommodityType description) { this.description = description; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public Integer getPieces() { return pieces; }
    public void setPieces(Integer pieces) { this.pieces = pieces; }
    public Integer getPiecesPct() { return piecesPct; }
    public void setPiecesPct(Integer piecesPct) { this.piecesPct = piecesPct; }
    public BigDecimal getTempInbound() { return tempInbound; }
    public void setTempInbound(BigDecimal tempInbound) { this.tempInbound = tempInbound; }
    public BigDecimal getTempOutbound() { return tempOutbound; }
    public void setTempOutbound(BigDecimal tempOutbound) { this.tempOutbound = tempOutbound; }
    public Boolean getHc() { return hc; }
    public void setHc(Boolean hc) { this.hc = hc; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public BigDecimal getConsumptionPallets() { return consumptionPallets; }
    public void setConsumptionPallets(BigDecimal consumptionPallets) { this.consumptionPallets = consumptionPallets; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public Integer getAvgTimePerPieceSec() { return avgTimePerPieceSec; }
    public void setAvgTimePerPieceSec(Integer avgTimePerPieceSec) { this.avgTimePerPieceSec = avgTimePerPieceSec; }
    public BigDecimal getLapseMinutes() { return lapseMinutes; }
    public BigDecimal getPcsPerMin() { return pcsPerMin; }
    public BigDecimal getOperativeWorkedHours() { return operativeWorkedHours; }
    public BigDecimal getEarnedHours() { return earnedHours; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

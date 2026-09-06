package com.aircargo.uldservice.dto;

import com.aircargo.common.entity.CommodityType;
import com.aircargo.uldservice.entity.UldAwb;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public class UldAwbDTO {

    private UUID id;
    private UUID uldId;
    private UUID mawbId;
    private String mawbLabel;
    private CommodityType description;
    private String destination;
    private String status;
    private Integer pieces;
    private Integer piecesPct;
    private BigDecimal tempInbound;
    private BigDecimal tempOutbound;
    private Boolean hc;
    private String comments;
    private BigDecimal consumptionPallets;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer avgTimePerPieceSec;
    private BigDecimal lapseMinutes;
    private BigDecimal pcsPerMin;
    private BigDecimal operativeWorkedHours;
    private BigDecimal earnedHours;
    private OffsetDateTime createdAt;

    public UldAwbDTO() {}

    public static UldAwbDTO fromEntity(UldAwb entity) {
        if (entity == null) return null;
        UldAwbDTO dto = new UldAwbDTO();
        dto.setId(entity.getId());
        dto.setUldId(entity.getUldId());
        dto.setMawbId(entity.getMawbId());
        dto.setMawbLabel(entity.getMawbLabel());
        dto.setDescription(entity.getDescription());
        dto.setDestination(entity.getDestination());
        dto.setStatus(entity.getStatus());
        dto.setPieces(entity.getPieces());
        dto.setPiecesPct(entity.getPiecesPct());
        dto.setTempInbound(entity.getTempInbound());
        dto.setTempOutbound(entity.getTempOutbound());
        dto.setHc(entity.getHc());
        dto.setComments(entity.getComments());
        dto.setConsumptionPallets(entity.getConsumptionPallets());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setAvgTimePerPieceSec(entity.getAvgTimePerPieceSec());
        dto.setLapseMinutes(entity.getLapseMinutes());
        dto.setPcsPerMin(entity.getPcsPerMin());
        dto.setOperativeWorkedHours(entity.getOperativeWorkedHours());
        dto.setEarnedHours(entity.getEarnedHours());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public static UldAwb toEntity(UldAwbDTO dto) {
        if (dto == null) return null;
        UldAwb entity = new UldAwb();
        entity.setId(dto.getId());
        entity.setUldId(dto.getUldId());
        entity.setMawbId(dto.getMawbId());
        entity.setMawbLabel(dto.getMawbLabel());
        entity.setDescription(dto.getDescription());
        entity.setDestination(dto.getDestination());
        entity.setStatus(dto.getStatus());
        entity.setPieces(dto.getPieces());
        entity.setPiecesPct(dto.getPiecesPct());
        entity.setTempInbound(dto.getTempInbound());
        entity.setTempOutbound(dto.getTempOutbound());
        entity.setHc(dto.getHc());
        entity.setComments(dto.getComments());
        entity.setConsumptionPallets(dto.getConsumptionPallets());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setAvgTimePerPieceSec(dto.getAvgTimePerPieceSec());
        return entity;
    }

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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
    public void setLapseMinutes(BigDecimal lapseMinutes) { this.lapseMinutes = lapseMinutes; }
    public BigDecimal getPcsPerMin() { return pcsPerMin; }
    public void setPcsPerMin(BigDecimal pcsPerMin) { this.pcsPerMin = pcsPerMin; }
    public BigDecimal getOperativeWorkedHours() { return operativeWorkedHours; }
    public void setOperativeWorkedHours(BigDecimal operativeWorkedHours) { this.operativeWorkedHours = operativeWorkedHours; }
    public BigDecimal getEarnedHours() { return earnedHours; }
    public void setEarnedHours(BigDecimal earnedHours) { this.earnedHours = earnedHours; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

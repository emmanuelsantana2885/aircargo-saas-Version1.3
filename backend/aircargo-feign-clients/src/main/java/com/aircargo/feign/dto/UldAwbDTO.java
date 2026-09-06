package com.aircargo.feign.dto;

import com.aircargo.common.entity.CommodityType;
import java.math.BigDecimal;
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

    public UldAwbDTO() {}

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
}
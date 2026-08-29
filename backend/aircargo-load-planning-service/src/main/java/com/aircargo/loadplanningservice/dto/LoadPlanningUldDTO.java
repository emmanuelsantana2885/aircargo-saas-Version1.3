package com.aircargo.loadplanningservice.dto;

import com.aircargo.feign.dto.UldAwbDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class LoadPlanningUldDTO {

    private UUID id;
    private String uldNumber;
    private String uldType;
    private String position;
    private String config;
    private String sealNumber;
    private BigDecimal tareLbs;
    private BigDecimal grossWeightLbs;
    private BigDecimal netWeightLbs;
    private String status;
    private List<UldAwbDTO> awbs;
    private String destination;
    private String builtBy;
    private String confirmedWith;
    private String completedAt;

    public LoadPlanningUldDTO() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUldNumber() { return uldNumber; }
    public void setUldNumber(String uldNumber) { this.uldNumber = uldNumber; }
    public String getUldType() { return uldType; }
    public void setUldType(String uldType) { this.uldType = uldType; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<UldAwbDTO> getAwbs() { return awbs; }
    public void setAwbs(List<UldAwbDTO> awbs) { this.awbs = awbs; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getBuiltBy() { return builtBy; }
    public void setBuiltBy(String builtBy) { this.builtBy = builtBy; }
    public String getConfirmedWith() { return confirmedWith; }
    public void setConfirmedWith(String confirmedWith) { this.confirmedWith = confirmedWith; }
    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
}

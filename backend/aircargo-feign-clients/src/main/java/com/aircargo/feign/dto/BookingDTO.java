package com.aircargo.feign.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class BookingDTO {
    private UUID id;
    private UUID airlineId;
    private UUID flightId;
    private UUID mawbId;
    private String clientName;
    private String contactName;
    private String cnee;
    private String shipperName;
    private String awbNumber;
    private Integer skids;
    private Integer units;
    private String eaType;
    private BigDecimal reservedKg;
    private BigDecimal confirmedKg;
    private BigDecimal receivedKg;
    private BigDecimal fulfillmentPct;
    private String destination;
    private String priority;
    private String commodityType;
    private LocalDate dayReceived;
    private String timeHours;
    private Integer positions;
    private Integer realPositions;
    private Boolean isConfirmed;
    private String notes;

    public BookingDTO() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAirlineId() { return airlineId; }
    public void setAirlineId(UUID airlineId) { this.airlineId = airlineId; }
    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
    public UUID getMawbId() { return mawbId; }
    public void setMawbId(UUID mawbId) { this.mawbId = mawbId; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getCnee() { return cnee; }
    public void setCnee(String cnee) { this.cnee = cnee; }
    public String getShipperName() { return shipperName; }
    public void setShipperName(String shipperName) { this.shipperName = shipperName; }
    public String getAwbNumber() { return awbNumber; }
    public void setAwbNumber(String awbNumber) { this.awbNumber = awbNumber; }
    public Integer getSkids() { return skids; }
    public void setSkids(Integer skids) { this.skids = skids; }
    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }
    public String getEaType() { return eaType; }
    public void setEaType(String eaType) { this.eaType = eaType; }
    public BigDecimal getReservedKg() { return reservedKg; }
    public void setReservedKg(BigDecimal reservedKg) { this.reservedKg = reservedKg; }
    public BigDecimal getConfirmedKg() { return confirmedKg; }
    public void setConfirmedKg(BigDecimal confirmedKg) { this.confirmedKg = confirmedKg; }
    public BigDecimal getReceivedKg() { return receivedKg; }
    public void setReceivedKg(BigDecimal receivedKg) { this.receivedKg = receivedKg; }
    public BigDecimal getFulfillmentPct() { return fulfillmentPct; }
    public void setFulfillmentPct(BigDecimal fulfillmentPct) { this.fulfillmentPct = fulfillmentPct; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getCommodityType() { return commodityType; }
    public void setCommodityType(String commodityType) { this.commodityType = commodityType; }
    public LocalDate getDayReceived() { return dayReceived; }
    public void setDayReceived(LocalDate dayReceived) { this.dayReceived = dayReceived; }
    public String getTimeHours() { return timeHours; }
    public void setTimeHours(String timeHours) { this.timeHours = timeHours; }
    public Integer getPositions() { return positions; }
    public void setPositions(Integer positions) { this.positions = positions; }
    public Integer getRealPositions() { return realPositions; }
    public void setRealPositions(Integer realPositions) { this.realPositions = realPositions; }
    public Boolean getIsConfirmed() { return isConfirmed; }
    public void setIsConfirmed(Boolean isConfirmed) { this.isConfirmed = isConfirmed; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

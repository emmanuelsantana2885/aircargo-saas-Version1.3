package com.aircargo.bookingservice.dto;

import com.aircargo.bookingservice.entity.Booking;
import com.aircargo.bookingservice.entity.EaType;
import com.aircargo.common.entity.Airline;
import com.aircargo.bookingservice.entity.Flight;
import com.aircargo.common.entity.CommodityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private EaType eaType;
    private BigDecimal reservedKg;
    private BigDecimal confirmedKg;
    private BigDecimal receivedKg;
    private BigDecimal fulfillmentPct;
    private String destination;
    private String priority;
    private CommodityType commodityType;
    private LocalDate dayReceived;
    private String timeHours;
    private Integer positions;
    private Integer realPositions;
    private BigDecimal lastWeekKg;
    private Integer lastWeekPositions;
    private Boolean isConfirmed;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static BookingDTO fromEntity(Booking booking) {
        if (booking == null) return null;
        return BookingDTO.builder()
                .id(booking.getId())
                .airlineId(booking.getAirline() != null ? booking.getAirline().getId() : null)
                .flightId(booking.getFlight() != null ? booking.getFlight().getId() : null)
                .mawbId(booking.getMawbId())
                .clientName(booking.getClientName())
                .contactName(booking.getContactName())
                .cnee(booking.getCnee())
                .shipperName(booking.getShipperName())
                .awbNumber(booking.getAwbNumber())
                .skids(booking.getSkids())
                .units(booking.getUnits())
                .eaType(booking.getEaType())
                .reservedKg(booking.getReservedKg())
                .confirmedKg(booking.getConfirmedKg())
                .receivedKg(booking.getReceivedKg())
                .fulfillmentPct(booking.getFulfillmentPct())
                .destination(booking.getDestination())
                .priority(booking.getPriority())
                .commodityType(booking.getCommodityType())
                .dayReceived(booking.getDayReceived())
                .timeHours(booking.getTimeHours())
                .positions(booking.getPositions())
                .realPositions(booking.getRealPositions())
                .lastWeekKg(booking.getLastWeekKg())
                .lastWeekPositions(booking.getLastWeekPositions())
                .isConfirmed(booking.getIsConfirmed())
                .notes(booking.getNotes())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    public static Booking toEntity(BookingDTO dto) {
        if (dto == null) return null;
        Booking entity = new Booking();
        entity.setId(dto.getId());
        if (dto.getAirlineId() != null) {
            Airline a = new Airline();
            a.setId(dto.getAirlineId());
            entity.setAirline(a);
        }
        if (dto.getFlightId() != null) {
            Flight f = new Flight();
            f.setId(dto.getFlightId());
            entity.setFlight(f);
        }
        entity.setMawbId(dto.getMawbId());
        entity.setClientName(dto.getClientName());
        entity.setContactName(dto.getContactName());
        entity.setCnee(dto.getCnee());
        entity.setShipperName(dto.getShipperName());
        entity.setAwbNumber(dto.getAwbNumber());
        entity.setSkids(dto.getSkids());
        entity.setUnits(dto.getUnits());
        entity.setEaType(dto.getEaType());
        entity.setReservedKg(dto.getReservedKg() != null ? dto.getReservedKg() : BigDecimal.ZERO);
        entity.setConfirmedKg(dto.getConfirmedKg());
        entity.setReceivedKg(dto.getReceivedKg());
        
        BigDecimal fp = dto.getFulfillmentPct();
        if (fp != null && fp.compareTo(BigDecimal.valueOf(9999.9999)) > 0) {
            fp = BigDecimal.valueOf(9999.9999);
        }
        entity.setFulfillmentPct(fp);
        
        entity.setDestination(dto.getDestination());
        entity.setPriority(dto.getPriority());
        entity.setCommodityType(dto.getCommodityType());
        entity.setDayReceived(dto.getDayReceived());
        entity.setTimeHours(dto.getTimeHours());
        entity.setPositions(dto.getPositions());
        entity.setRealPositions(dto.getRealPositions());
        entity.setLastWeekKg(dto.getLastWeekKg());
        entity.setLastWeekPositions(dto.getLastWeekPositions());
        entity.setIsConfirmed(dto.getIsConfirmed() != null ? dto.getIsConfirmed() : false);
        entity.setNotes(dto.getNotes());
        return entity;
    }
}
package com.aircargo.exportservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WeightReportRow(
    String awbNumber,
    String shipperName,
    String consigneeName,
    String destination,
    String commodityType,
    String flightNumber,
    LocalDate flightDate,
    UUID flightId,
    UUID mawbId,
    Integer receivedPieces,
    BigDecimal receivedWeightKg,
    BigDecimal physicalWeightLbs,
    BigDecimal chargeableWeightLbs,
    BigDecimal chargeableWeightKg,
    Integer dispatchedPieces,
    BigDecimal dispatchedWeightLbs
) {}

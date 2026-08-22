package com.aircargo.exportservice.service;

import com.aircargo.exportservice.dto.WeightReportRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WeightReportService {

    private static final BigDecimal KG_TO_LBS = new BigDecimal("2.20462");

    @PersistenceContext
    private EntityManager em;

    public List<WeightReportRow> getWeightReport(
            LocalDate dateFrom, LocalDate dateTo,
            UUID flightId, String commodityType,
            String awbNumber, String shipperName,
            String consigneeName, String destination,
            String hawbNumber) {

        StringBuilder sql = new StringBuilder("""
            SELECT
                m.awb_number,
                m.shipper_name,
                m.consignee_name,
                m.destination,
                m.commodity_type,
                f.flight_number,
                f.flight_date,
                f.id AS flight_id,
                m.id AS mawb_id,
                COALESCE(r.piece_count, m.pieces, 0) AS received_pieces,
                COALESCE(r.actual_weight_kg, m.reported_weight_kg, 0) AS received_weight_kg,
                COALESCE(r.actual_weight_lbs, 0) AS physical_weight_lbs,
                COALESCE(r.chargeable_weight_lbs, 0) AS chargeable_weight_lbs,
                COALESCE(r.chargeable_weight_kg, 0) AS chargeable_weight_kg
            FROM mawb m
            INNER JOIN flight f ON f.id = m.flight_id
            LEFT JOIN warehouse_receipt r ON r.mawb_id = m.id AND r.superseded = false
            WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (dateFrom != null) {
            sql.append(" AND f.flight_date >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null) {
            sql.append(" AND f.flight_date <= ?");
            params.add(dateTo);
        }
        if (flightId != null) {
            sql.append(" AND f.id = ?");
            params.add(flightId);
        }
        if (commodityType != null && !commodityType.isBlank()) {
            sql.append(" AND m.commodity_type = ?::commodity_type");
            params.add(commodityType);
        }
        if (awbNumber != null && !awbNumber.isBlank()) {
            sql.append(" AND m.awb_number ILIKE ?");
            params.add("%" + awbNumber + "%");
        }
        if (shipperName != null && !shipperName.isBlank()) {
            sql.append(" AND m.shipper_name ILIKE ?");
            params.add("%" + shipperName + "%");
        }
        if (consigneeName != null && !consigneeName.isBlank()) {
            sql.append(" AND m.consignee_name ILIKE ?");
            params.add("%" + consigneeName + "%");
        }
        if (destination != null && !destination.isBlank()) {
            sql.append(" AND m.destination ILIKE ?");
            params.add("%" + destination + "%");
        }

        sql.append(" ORDER BY f.flight_date DESC, m.awb_number ASC");

        var query = em.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        List<Object[]> rows = query.getResultList();

        // Build base rows
        Map<String, WeightReportRow> rowMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String awb = (String) row[0];
            String shipper = (String) row[1];
            String consignee = (String) row[2];
            String dest = (String) row[3];
            String commodity = (String) row[4];
            String flightNum = (String) row[5];
            LocalDate flightDate = null;
            if (row[6] instanceof java.sql.Date sd) {
                flightDate = sd.toLocalDate();
            } else if (row[6] instanceof LocalDate ld) {
                flightDate = ld;
            } else if (row[6] != null) {
                flightDate = LocalDate.parse(row[6].toString().substring(0, 10));
            }
            UUID fId = row[7] != null ? UUID.fromString(row[7].toString()) : null;
            UUID mId = row[8] != null ? UUID.fromString(row[8].toString()) : null;
            Integer receivedPcs = row[9] != null ? ((Number) row[9]).intValue() : 0;
            BigDecimal receivedKg = row[10] != null ? new BigDecimal(row[10].toString()) : BigDecimal.ZERO;
            BigDecimal physLbs = row[11] != null ? new BigDecimal(row[11].toString()) : BigDecimal.ZERO;
            BigDecimal chargeLbs = row[12] != null ? new BigDecimal(row[12].toString()) : BigDecimal.ZERO;
            BigDecimal chargeKg = row[13] != null ? new BigDecimal(row[13].toString()) : BigDecimal.ZERO;

            String key = fId + "|" + mId;
            rowMap.put(key, new WeightReportRow(
                awb, shipper, consignee, dest, commodity,
                flightNum, flightDate, fId, mId,
                receivedPcs, receivedKg, physLbs, chargeLbs, chargeKg,
                0, BigDecimal.ZERO
            ));
        }

        // Now fetch dispatched pieces per (flight, mawb) via uld_awb -> uld
        if (!rowMap.isEmpty()) {
            List<UUID> flightIds = rowMap.values().stream()
                .map(WeightReportRow::flightId).filter(Objects::nonNull).distinct().toList();

            if (!flightIds.isEmpty()) {
                // Fetch ULDs for these flights
                var uldQuery = em.createNativeQuery(
                    "SELECT id, flight_id FROM uld WHERE flight_id IN :fids");
                uldQuery.setParameter("fids", flightIds);
                List<Object[]> uldRows = uldQuery.getResultList();

                Map<UUID, Set<UUID>> flightToUldIds = new HashMap<>();
                for (Object[] urow : uldRows) {
                    UUID uldId = UUID.fromString(urow[0].toString());
                    UUID fid = UUID.fromString(urow[1].toString());
                    flightToUldIds.computeIfAbsent(fid, k -> new HashSet<>()).add(uldId);
                }

                // Fetch ULD-AWB links
                Set<UUID> allUldIds = flightToUldIds.values().stream()
                    .flatMap(Set::stream).collect(Collectors.toSet());

                if (!allUldIds.isEmpty()) {
                    var uldAwbQuery = em.createNativeQuery(
                        "SELECT uld_id, mawb_id, pieces FROM uld_awb WHERE uld_id IN :uids");
                    uldAwbQuery.setParameter("uids", allUldIds);
                    List<Object[]> uldAwbRows = uldAwbQuery.getResultList();

                    // Map: flightId -> mawbId -> total dispatched pieces
                    Map<UUID, Map<UUID, Integer>> dispatched = new HashMap<>();
                    for (Object[] arow : uldAwbRows) {
                        UUID uldId = UUID.fromString(arow[0].toString());
                        UUID mawbId = arow[1] != null ? UUID.fromString(arow[1].toString()) : null;
                        int pcs = arow[2] != null ? ((Number) arow[2]).intValue() : 0;
                        if (mawbId == null) continue;

                        // find which flight this uld belongs to
                        for (var entry : flightToUldIds.entrySet()) {
                            if (entry.getValue().contains(uldId)) {
                                dispatched.computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                                    .merge(mawbId, pcs, Integer::sum);
                                break;
                            }
                        }
                    }

                    // Enrich rows with dispatched data
                    Map<String, WeightReportRow> enriched = new LinkedHashMap<>();
                    for (var entry : rowMap.entrySet()) {
                        WeightReportRow r = entry.getValue();
                        Integer dispPcs = dispatched
                            .getOrDefault(r.flightId(), Collections.emptyMap())
                            .getOrDefault(r.mawbId(), 0);
                        BigDecimal dispLbs = BigDecimal.ZERO;
                        if (dispPcs > 0 && r.receivedPieces() != null && r.receivedPieces() > 0 && r.receivedWeightKg() != null) {
                            BigDecimal perPieceKg = r.receivedWeightKg().divide(BigDecimal.valueOf(r.receivedPieces()), 6, RoundingMode.HALF_UP);
                            dispLbs = perPieceKg.multiply(BigDecimal.valueOf(dispPcs)).multiply(KG_TO_LBS).setScale(2, RoundingMode.HALF_UP);
                        }
                        enriched.put(entry.getKey(), new WeightReportRow(
                            r.awbNumber(), r.shipperName(), r.consigneeName(), r.destination(),
                            r.commodityType(), r.flightNumber(), r.flightDate(), r.flightId(), r.mawbId(),
                            r.receivedPieces(), r.receivedWeightKg(), r.physicalWeightLbs(),
                            r.chargeableWeightLbs(), r.chargeableWeightKg(),
                            dispPcs, dispLbs
                        ));
                    }
                    return new ArrayList<>(enriched.values());
                }
            }
        }

        return new ArrayList<>(rowMap.values());
    }

    public Map<String, Object> getWeightSummary(
            LocalDate dateFrom, LocalDate dateTo,
            UUID flightId, String commodityType,
            String awbNumber, String shipperName,
            String consigneeName, String destination,
            String hawbNumber) {

        List<WeightReportRow> rows = getWeightReport(
            dateFrom, dateTo, flightId, commodityType,
            awbNumber, shipperName, consigneeName, destination, hawbNumber);

        // Group by commodity type
        Map<String, Map<String, Object>> byCommodity = rows.stream()
            .collect(Collectors.groupingBy(
                r -> r.commodityType() != null ? r.commodityType() : "UNKNOWN",
                LinkedHashMap::new,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("count", list.size());
                        m.put("totalReceivedPieces", list.stream().mapToInt(r -> r.receivedPieces() != null ? r.receivedPieces() : 0).sum());
                        m.put("totalDispatchedPieces", list.stream().mapToInt(r -> r.dispatchedPieces() != null ? r.dispatchedPieces() : 0).sum());
                        m.put("totalPhysicalWeightLbs", list.stream().map(r -> r.physicalWeightLbs() != null ? r.physicalWeightLbs() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
                        m.put("totalChargeableWeightLbs", list.stream().map(r -> r.chargeableWeightLbs() != null ? r.chargeableWeightLbs() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
                        m.put("totalDispatchedWeightLbs", list.stream().map(r -> r.dispatchedWeightLbs() != null ? r.dispatchedWeightLbs() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
                        return m;
                    }
                )
            ));

        // Group by flight
        Map<String, Map<String, Object>> byFlight = rows.stream()
            .filter(r -> r.flightNumber() != null)
            .collect(Collectors.groupingBy(
                r -> r.flightNumber(),
                LinkedHashMap::new,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("flightDate", list.get(0).flightDate());
                        m.put("count", list.size());
                        m.put("totalReceivedPieces", list.stream().mapToInt(r -> r.receivedPieces() != null ? r.receivedPieces() : 0).sum());
                        m.put("totalDispatchedPieces", list.stream().mapToInt(r -> r.dispatchedPieces() != null ? r.dispatchedPieces() : 0).sum());
                        m.put("totalPhysicalWeightLbs", list.stream().map(r -> r.physicalWeightLbs() != null ? r.physicalWeightLbs() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
                        m.put("totalChargeableWeightLbs", list.stream().map(r -> r.chargeableWeightLbs() != null ? r.chargeableWeightLbs() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
                        m.put("totalDispatchedWeightLbs", list.stream().map(r -> r.dispatchedWeightLbs() != null ? r.dispatchedWeightLbs() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
                        return m;
                    }
                )
            ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRows", rows.size());
        result.put("totalReceivedPieces", rows.stream().mapToInt(r -> r.receivedPieces() != null ? r.receivedPieces() : 0).sum());
        result.put("totalDispatchedPieces", rows.stream().mapToInt(r -> r.dispatchedPieces() != null ? r.dispatchedPieces() : 0).sum());
        result.put("totalPhysicalWeightLbs", rows.stream().map(r -> r.physicalWeightLbs() != null ? r.physicalWeightLbs() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("totalChargeableWeightLbs", rows.stream().map(r -> r.chargeableWeightLbs() != null ? r.chargeableWeightLbs() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("totalDispatchedWeightLbs", rows.stream().map(r -> r.dispatchedWeightLbs() != null ? r.dispatchedWeightLbs() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("byCommodity", byCommodity);
        result.put("byFlight", byFlight);
        return result;
    }
}

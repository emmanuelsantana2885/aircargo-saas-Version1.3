package com.aircargo.exportservice.service;

import com.aircargo.exportservice.dto.CalculatedFieldDTO;
import com.aircargo.exportservice.dto.EvaluateResult;
import com.aircargo.exportservice.dto.FieldDefDTO;
import com.aircargo.exportservice.dto.FilterDTO;
import com.aircargo.exportservice.dto.PivotConfig;
import com.aircargo.exportservice.dto.PivotResult;
import com.aircargo.exportservice.dto.PivotRow;
import com.aircargo.exportservice.dto.PivotValue;
import com.aircargo.exportservice.dto.ReportConfigDTO;
import com.aircargo.exportservice.entity.AirlineEntity;
import com.aircargo.exportservice.entity.BookingEntity;
import com.aircargo.exportservice.entity.FlightEntity;
import com.aircargo.exportservice.entity.MawbEntity;
import com.aircargo.exportservice.entity.UldAwbEntity;
import com.aircargo.exportservice.entity.UldEntity;
import com.aircargo.exportservice.entity.WarehouseReceiptEntity;
import com.aircargo.exportservice.repository.AirlineRepository;
import com.aircargo.exportservice.repository.BookingRepository;
import com.aircargo.exportservice.repository.FlightRepository;
import com.aircargo.exportservice.repository.MawbRepository;
import com.aircargo.exportservice.repository.UldAwbRepository;
import com.aircargo.exportservice.repository.UldRepository;
import com.aircargo.exportservice.repository.WarehouseReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lógica del Dashboard Builder: reporte calculable estilo hoja de cálculo.
 *
 * <p>Cada fila del reporte es un registro {@code uld_awb} (un MAWB dentro de un ULD)
 * enriquecido con los datos de ULD, vuelo (y aerolínea), MAWB, booking y recibo de
 * bodega. El operador selecciona qué campos de qué tabla incluir en el reporte
 * ({@code fieldSources}), agrupa por una dimensión, aplica filtros (WHERE),
 * define fórmulas por fila y variables de escenario.</p>
 */
@Service
public class DashboardBuilderService {

    private final UldRepository uldRepository;
    private final UldAwbRepository uldAwbRepository;
    private final FlightRepository flightRepository;
    private final MawbRepository mawbRepository;
    private final BookingRepository bookingRepository;
    private final WarehouseReceiptRepository receiptRepository;
    private final AirlineRepository airlineRepository;

    private static final int MAX_ROWS = 5000;

    /** Campos propios del ULD: al agrupar se suman por ULD distinto (no por ULD-AWB). */
    private static final Set<String> ULD_LEVEL_KEYS = Set.of(
            "TareLbs", "GrossLbs", "NetLbs", "TareKg", "GrossKg", "NetKg");
    /** Campos propios del vuelo: al agrupar se suman por vuelo distinto. */
    private static final Set<String> FLIGHT_LEVEL_KEYS = Set.of("MaxPayloadKg");

    public DashboardBuilderService(UldRepository uldRepository,
                                   UldAwbRepository uldAwbRepository,
                                   FlightRepository flightRepository,
                                   MawbRepository mawbRepository,
                                   BookingRepository bookingRepository,
                                   WarehouseReceiptRepository receiptRepository,
                                   AirlineRepository airlineRepository) {
        this.uldRepository = uldRepository;
        this.uldAwbRepository = uldAwbRepository;
        this.flightRepository = flightRepository;
        this.mawbRepository = mawbRepository;
        this.bookingRepository = bookingRepository;
        this.receiptRepository = receiptRepository;
        this.airlineRepository = airlineRepository;
    }

    /** Catálogo de campos disponibles por tabla (fuente principal: ULD-AWB). */
    public List<FieldDefDTO> fields() {
        List<FieldDefDTO> list = new ArrayList<>();
        // ULD
        list.add(new FieldDefDTO("UldNumber", "N° ULD", "", "string", "uld", ""));
        list.add(new FieldDefDTO("UldType", "Tipo ULD", "", "string", "uld", ""));
        list.add(new FieldDefDTO("Status", "Estado ULD", "", "string", "uld", ""));
        list.add(new FieldDefDTO("Position", "Posición", "", "string", "uld", ""));
        list.add(new FieldDefDTO("SealNumber", "Sello", "", "string", "uld", ""));
        list.add(new FieldDefDTO("TareLbs", "Tara (lbs)", "lbs", "number", "uld", ""));
        list.add(new FieldDefDTO("GrossLbs", "Gross (lbs)", "lbs", "number", "uld", ""));
        list.add(new FieldDefDTO("NetLbs", "Net (lbs)", "lbs", "number", "uld", "NetLbs = GrossLbs - TareLbs"));
        list.add(new FieldDefDTO("TareKg", "Tara (kg)", "kg", "number", "uld", ""));
        list.add(new FieldDefDTO("GrossKg", "Gross (kg)", "kg", "number", "uld", ""));
        list.add(new FieldDefDTO("NetKg", "Net (kg)", "kg", "number", "uld", ""));
        // Vuelo + aerolínea
        list.add(new FieldDefDTO("FlightNumber", "Vuelo", "", "string", "flight", "Se une por flight_id"));
        list.add(new FieldDefDTO("FlightDate", "Fecha vuelo", "", "date", "flight", ""));
        list.add(new FieldDefDTO("Origin", "Origen", "", "string", "flight", ""));
        list.add(new FieldDefDTO("Destination", "Destino", "", "string", "flight", ""));
        list.add(new FieldDefDTO("AircraftReg", "Matrícula", "", "string", "flight", ""));
        list.add(new FieldDefDTO("AircraftType", "Tipo aeronave", "", "string", "flight", ""));
        list.add(new FieldDefDTO("FlightStatus", "Estado vuelo", "", "string", "flight", ""));
        list.add(new FieldDefDTO("MaxPayloadKg", "Payload máx (kg)", "kg", "number", "flight", ""));
        // Aerolínea
        list.add(new FieldDefDTO("AirlineCode", "Aerolínea (código)", "", "string", "airline", "Se une por airline_id"));
        list.add(new FieldDefDTO("AirlineName", "Aerolínea (nombre)", "", "string", "airline", ""));
        // MAWB
        list.add(new FieldDefDTO("AwbNumber", "N° MAWB", "", "string", "mawb", ""));
        list.add(new FieldDefDTO("MawbShipper", "Remitente", "", "string", "mawb", ""));
        list.add(new FieldDefDTO("MawbConsignee", "Consignatario", "", "string", "mawb", ""));
        list.add(new FieldDefDTO("MawbOrigin", "Origen MAWB", "", "string", "mawb", ""));
        list.add(new FieldDefDTO("MawbDestination", "Destino MAWB", "", "string", "mawb", ""));
        list.add(new FieldDefDTO("MawbPieces", "Piezas MAWB", "pcs", "number", "mawb", ""));
        list.add(new FieldDefDTO("ReportedWeightKg", "Peso reportado (kg)", "kg", "number", "mawb", ""));
        list.add(new FieldDefDTO("ChargeableWeightKg", "Peso cobrable (kg)", "kg", "number", "mawb", ""));
        list.add(new FieldDefDTO("MawbCommodity", "Commodity", "", "string", "mawb", ""));
        list.add(new FieldDefDTO("MawbStatus", "Estado MAWB", "", "string", "mawb", ""));
        list.add(new FieldDefDTO("CashOnly", "Solo efectivo", "", "boolean", "mawb", ""));
        list.add(new FieldDefDTO("MawbPreBuilt", "Pre-built", "", "boolean", "mawb", ""));
        // Booking
        list.add(new FieldDefDTO("BookingClient", "Cliente", "", "string", "booking", ""));
        list.add(new FieldDefDTO("BookingShipper", "Remitente (booking)", "", "string", "booking", ""));
        list.add(new FieldDefDTO("BookingCnee", "Consignatario (booking)", "", "string", "booking", ""));
        list.add(new FieldDefDTO("BookingDestination", "Destino (booking)", "", "string", "booking", ""));
        list.add(new FieldDefDTO("Skids", "Skids", "pcs", "number", "booking", ""));
        list.add(new FieldDefDTO("Units", "Unidades", "pcs", "number", "booking", ""));
        list.add(new FieldDefDTO("ReservedKg", "Reservado (kg)", "kg", "number", "booking", ""));
        list.add(new FieldDefDTO("ConfirmedKg", "Confirmado (kg)", "kg", "number", "booking", ""));
        list.add(new FieldDefDTO("ReceivedKg", "Recibido (kg)", "kg", "number", "booking", ""));
        list.add(new FieldDefDTO("FulfillmentPct", "Cumplimiento %", "%", "number", "booking", ""));
        list.add(new FieldDefDTO("BookingCommodity", "Commodity (booking)", "", "string", "booking", ""));
        list.add(new FieldDefDTO("Priority", "Prioridad", "", "string", "booking", ""));
        list.add(new FieldDefDTO("IsConfirmed", "Confirmado", "", "boolean", "booking", ""));
        // Recibo de bodega
        list.add(new FieldDefDTO("ReceiptPieces", "Piezas recibidas", "pcs", "number", "receipt", ""));
        list.add(new FieldDefDTO("AwbReportedPieces", "Piezas reportadas AWB", "pcs", "number", "receipt", ""));
        list.add(new FieldDefDTO("ActualWeightKg", "Peso real (kg)", "kg", "number", "receipt", ""));
        list.add(new FieldDefDTO("ChargeableKg", "Cobrable recibo (kg)", "kg", "number", "receipt", ""));
        list.add(new FieldDefDTO("ActualWeightLbs", "Peso real (lbs)", "lbs", "number", "receipt", ""));
        list.add(new FieldDefDTO("ChargeableLbs", "Cobrable recibo (lbs)", "lbs", "number", "receipt", ""));
        list.add(new FieldDefDTO("ReceiptDate", "Fecha recibo", "", "date", "receipt", ""));
        list.add(new FieldDefDTO("CreatedByName", "Recibido por", "", "string", "receipt", ""));
        list.add(new FieldDefDTO("RcptCashOnly", "Cash only (recibo)", "", "boolean", "receipt", ""));
        list.add(new FieldDefDTO("BookedInAcoms", "Booked en ACOMS", "", "boolean", "receipt", ""));
        list.add(new FieldDefDTO("DocsProvided", "Doc. provistas", "", "boolean", "receipt", ""));
        list.add(new FieldDefDTO("CustomsCompleted", "Aduana completada", "", "boolean", "receipt", ""));
        list.add(new FieldDefDTO("RcptPreBuilt", "Pre-built (recibo)", "", "boolean", "receipt", ""));
        // Histórico compatible: piezas de la fila ULD-AWB
        list.add(new FieldDefDTO("Pieces", "Piezas (ULD-AWB)", "pcs", "number", "uld", "Piezas de este MAWB en el ULD"));
        // Escenario
        list.add(new FieldDefDTO("tasaCrecimiento", "Tasa crecimiento (escenario)", "ratio", "number", "scenario", "Variable de escenario"));
        return list;
    }

    /**
     * Evalúa un ReportConfigDTO. Construye una fila por {@code uld_awb}, une ULD /
     * vuelo / aerolínea / MAWB / booking / recibo, aplica filtros, agrupa por dimensión
     * y calcula totales. Las columnas de salida respetan {@code fieldSources}.
     */
    @Transactional(readOnly = true)
    public EvaluateResult evaluate(ReportConfigDTO cfg) {
        // 1. Cargar todas las entidades en mapas (independiente de la tabla base)
        List<MawbEntity> mawbsAll = mawbRepository.findAll();
        if (mawbsAll.size() > MAX_ROWS) mawbsAll = mawbsAll.subList(0, MAX_ROWS);
        List<UldAwbEntity> awbs = uldAwbRepository.findAll();
        if (awbs.size() > MAX_ROWS) awbs = awbs.subList(0, MAX_ROWS);

        Map<UUID, UldEntity> uldById = new HashMap<>();
        Map<UUID, FlightEntity> flightById = new HashMap<>();
        Map<UUID, AirlineEntity> airlineById = new HashMap<>();
        airlineRepository.findAll().forEach(a -> airlineById.put(a.getId(), a));
        uldRepository.findAll().forEach(u -> uldById.put(u.getId(), u));
        flightRepository.findAll().forEach(f -> flightById.put(f.getId(), f));
        Map<UUID, MawbEntity> mawbById = new HashMap<>();
        mawbsAll.forEach(m -> mawbById.put(m.getId(), m));

        // ULD-AWB: indexar por ULD y por MAWB para enriquecer filas según la tabla base
        Map<UUID, List<UldAwbEntity>> awbByUld = new HashMap<>();
        Map<UUID, List<UldAwbEntity>> awbByMawb = new HashMap<>();
        Map<UUID, UldEntity> uldForAwb = new HashMap<>();
        for (UldAwbEntity aw : awbs) {
            if (aw.getUldId() != null) {
                awbByUld.computeIfAbsent(aw.getUldId(), k -> new ArrayList<>()).add(aw);
                uldForAwb.put(aw.getUldId(), uldById.get(aw.getUldId()));
            }
            if (aw.getMawbId() != null) awbByMawb.computeIfAbsent(aw.getMawbId(), k -> new ArrayList<>()).add(aw);
        }

        // Booking: por mawb_id y por awb_number (fallback)
        Map<UUID, BookingEntity> bookingByMawbId = new HashMap<>();
        Map<String, BookingEntity> bookingByAwb = new HashMap<>();
        for (BookingEntity b : bookingRepository.findAll()) {
            if (b.getMawbId() != null && !bookingByMawbId.containsKey(b.getMawbId())) bookingByMawbId.put(b.getMawbId(), b);
            if (b.getAwbNumber() != null && !bookingByAwb.containsKey(b.getAwbNumber())) bookingByAwb.put(b.getAwbNumber(), b);
        }

        // Recibo: preferir el no reemplazado por MAWB
        Map<UUID, WarehouseReceiptEntity> receiptByMawbId = new HashMap<>();
        Map<UUID, WarehouseReceiptEntity> receiptFallback = new HashMap<>();
        for (WarehouseReceiptEntity r : receiptRepository.findAll()) {
            UUID mid = r.getMawbId();
            if (mid == null) continue;
            if (Boolean.TRUE.equals(r.getSuperseded())) {
                if (!receiptByMawbId.containsKey(mid)) receiptFallback.putIfAbsent(mid, r);
            } else {
                receiptByMawbId.putIfAbsent(mid, r);
            }
        }
        receiptByMawbId.putAll(receiptFallback);

        // 2. Variables de escenario numéricas
        Map<String, BigDecimal> scenario = new HashMap<>();
        if (cfg.scenario() != null) {
            cfg.scenario().forEach((k, v) -> {
                if (v instanceof Number n) scenario.put(FormulaEngine.normalizeKey(k), new BigDecimal(n.toString()));
                else if (v instanceof String s) {
                    try { scenario.put(FormulaEngine.normalizeKey(k), new BigDecimal(s.trim())); }
                    catch (NumberFormatException ignored) {}
                }
            });
        }

        // 3. Fila "cruda" según la tabla base (dataset de origen)
        List<Map<String, Object>> rawRows = buildRawRows(baseSourceOf(cfg), mawbsAll, awbs,
                uldById, flightById, airlineById, mawbById, bookingByMawbId, bookingByAwb, receiptByMawbId,
                awbByUld, awbByMawb);

        // 4. Filtros (WHERE)
        rawRows.removeIf(row -> !matchesFilters(row, filtersOf(cfg)));

        // 5. Agrupar por dimensión (si se pide)
        String dim = cfg.dimension() == null || cfg.dimension().isBlank() ? null : FormulaEngine.normalizeKey(cfg.dimension());
        List<Map<String, Object>> groupedRows = dim == null ? rawRows : groupBy(rawRows, dim);

        // 6. Aplicar columnas calculadas por fila
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> raw : groupedRows) {
            Map<String, BigDecimal> vars = varsFor(raw, scenario);
            Map<String, Object> out = new LinkedHashMap<>(raw);
            if (cfg.formulas() != null) {
                for (CalculatedFieldDTO cf : cfg.formulas()) {
                    if (cf.expression() == null || cf.expression().isBlank()) continue;
                    Double v = FormulaEngine.evaluate(cf.expression(), vars);
                    out.put(FormulaEngine.normalizeKey(cf.column()), v);
                }
            }
            rows.add(out);
        }

        // 6b. Top N: limitar a las N filas de mayor valor del eje Y (o primera columna numérica)
        int topN = topNOf(cfg);
        if (topN > 0 && rows.size() > topN) {
            String sortCol = chartYOf(cfg, rows);
            rows.sort((a, b) -> Double.compare(doubleOrZero(b.get(sortCol)), doubleOrZero(a.get(sortCol))));
            rows = new ArrayList<>(rows.subList(0, topN));
        }

        // 7. Columnas visibles: dimensión + fieldSources (o todas) + fórmulas
        LinkedHashSet<String> cols = new LinkedHashSet<>();
        if (dim != null) cols.add(dim);
        List<String> wanted = cfg.fieldSources() == null || cfg.fieldSources().isEmpty()
                ? null
                : cfg.fieldSources().stream().map(FormulaEngine::normalizeKey).toList();
        if (wanted == null) {
            for (Map<String, Object> r : rows) {
                r.keySet().stream().filter(k -> !k.startsWith("__")).forEach(cols::add);
            }
        } else {
            cols.addAll(wanted);
        }
        if (cfg.formulas() != null) {
            for (CalculatedFieldDTO cf : cfg.formulas()) {
                if (cf.column() != null && !cf.column().isBlank()) cols.add(FormulaEngine.normalizeKey(cf.column()));
            }
        }

        // 8. Recortar filas a las columnas visibles
        List<Map<String, Object>> trimmed = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> tr = new LinkedHashMap<>();
            for (String c : cols) if (r.containsKey(c)) tr.put(c, r.get(c));
            trimmed.add(tr);
        }

        // 9. Totales (agregación por columna: SUM / AVG / MAX / MIN / COUNT)
        List<Map<String, Object>> totals = totalsFor(rows, cols, cfg);

        return new EvaluateResult(trimmed, totals, new ArrayList<>(cols));
    }

    /**
     * Tabla dinámica (pivot). Reusa las filas crudas de la tabla base, aplica los filtros
     * y agrupa por las dimensiones de fila {@code rows}. Si se indica {@code column}, sus
     * valores distintos se convierten en grupos de columnas; para cada grupo de fila x
     * columna se aplica la agregación de cada medida en {@code values}.
     */
    @Transactional(readOnly = true)
    public PivotResult pivot(PivotConfig cfg) {
        List<MawbEntity> mawbsAll = mawbRepository.findAll();
        if (mawbsAll.size() > MAX_ROWS) mawbsAll = mawbsAll.subList(0, MAX_ROWS);
        List<UldAwbEntity> awbs = uldAwbRepository.findAll();
        if (awbs.size() > MAX_ROWS) awbs = awbs.subList(0, MAX_ROWS);

        Map<UUID, UldEntity> uldById = new HashMap<>();
        Map<UUID, FlightEntity> flightById = new HashMap<>();
        Map<UUID, AirlineEntity> airlineById = new HashMap<>();
        airlineRepository.findAll().forEach(a -> airlineById.put(a.getId(), a));
        uldRepository.findAll().forEach(u -> uldById.put(u.getId(), u));
        flightRepository.findAll().forEach(f -> flightById.put(f.getId(), f));
        Map<UUID, MawbEntity> mawbById = new HashMap<>();
        mawbsAll.forEach(m -> mawbById.put(m.getId(), m));

        Map<UUID, List<UldAwbEntity>> awbByUld = new HashMap<>();
        Map<UUID, List<UldAwbEntity>> awbByMawb = new HashMap<>();
        for (UldAwbEntity aw : awbs) {
            if (aw.getUldId() != null) awbByUld.computeIfAbsent(aw.getUldId(), k -> new ArrayList<>()).add(aw);
            if (aw.getMawbId() != null) awbByMawb.computeIfAbsent(aw.getMawbId(), k -> new ArrayList<>()).add(aw);
        }
        Map<UUID, BookingEntity> bookingByMawbId = new HashMap<>();
        Map<String, BookingEntity> bookingByAwb = new HashMap<>();
        for (BookingEntity b : bookingRepository.findAll()) {
            if (b.getMawbId() != null && !bookingByMawbId.containsKey(b.getMawbId())) bookingByMawbId.put(b.getMawbId(), b);
            if (b.getAwbNumber() != null && !bookingByAwb.containsKey(b.getAwbNumber())) bookingByAwb.put(b.getAwbNumber(), b);
        }
        Map<UUID, WarehouseReceiptEntity> receiptByMawbId = new HashMap<>();
        Map<UUID, WarehouseReceiptEntity> receiptFallback = new HashMap<>();
        for (WarehouseReceiptEntity r : receiptRepository.findAll()) {
            UUID mid = r.getMawbId();
            if (mid == null) continue;
            if (Boolean.TRUE.equals(r.getSuperseded())) {
                if (!receiptByMawbId.containsKey(mid)) receiptFallback.putIfAbsent(mid, r);
            } else {
                receiptByMawbId.putIfAbsent(mid, r);
            }
        }
        receiptByMawbId.putAll(receiptFallback);

        String base = cfg.baseSource() == null || cfg.baseSource().isBlank() ? "mawb" : cfg.baseSource().trim().toLowerCase();
        List<Map<String, Object>> rawRows = buildRawRows(base, mawbsAll, awbs, uldById, flightById, airlineById,
                mawbById, bookingByMawbId, bookingByAwb, receiptByMawbId, awbByUld, awbByMawb);
        rawRows.removeIf(row -> !matchesFilters(row, filtersOfChart(cfg.chartConfig())));

        List<String> rowFields = cfg.rows() == null ? List.of()
                : cfg.rows().stream().filter(r -> r != null && !r.isBlank()).map(FormulaEngine::normalizeKey).distinct().toList();
        List<PivotValue> values = cfg.values() == null ? List.of()
                : cfg.values().stream().filter(v -> v != null && v.field() != null && !v.field().isBlank()).toList();
        if (values.isEmpty() && !rowFields.isEmpty()) values = List.of(new PivotValue(rowFields.get(0), "COUNT"));

        String column = cfg.column() == null || cfg.column().isBlank() ? null : FormulaEngine.normalizeKey(cfg.column());
        List<Object> colGroups = new ArrayList<>();
        if (column == null) {
            colGroups.add("__total");
        } else {
            Set<Object> seen = new LinkedHashSet<>();
            for (Map<String, Object> r : rawRows) {
                Object v = r.get(column);
                if (v == null || "".equals(v)) v = "(sin valor)";
                if (seen.add(v)) colGroups.add(v);
            }
            if (colGroups.isEmpty()) colGroups.add("__total");
        }

        List<String> measureLabels = new ArrayList<>();
        for (PivotValue v : values) measureLabels.add(aggLabel(v.agg(), v.field()));

        int mCount = values.size();
        // Agrupar filas por la tupla de dimensiones
        Map<List<Object>, List<Map<String, Object>>> byRow = new LinkedHashMap<>();
        for (Map<String, Object> r : rawRows) {
            List<Object> key = new ArrayList<>();
            for (String rf : rowFields) {
                Object v = r.get(rf);
                key.add(v == null || "".equals(v) ? null : v);
            }
            if (key.isEmpty()) key.add(rowFields.isEmpty() ? "__single" : null);
            byRow.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<PivotRow> rows = new ArrayList<>();
        List<Object> grandTotals = new ArrayList<>(Collections.nCopies(colGroups.size() * mCount, null));
        for (Map.Entry<List<Object>, List<Map<String, Object>>> e : byRow.entrySet()) {
            List<Object> cells = new ArrayList<>(Collections.nCopies(colGroups.size() * mCount, null));
            for (int ci = 0; ci < colGroups.size(); ci++) {
                Object colVal = colGroups.get(ci);
                List<Map<String, Object>> subset = colVal.equals("__total")
                        ? e.getValue()
                        : e.getValue().stream().filter(r -> {
                            Object v = r.get(column);
                            if (v == null || "".equals(v)) v = "(sin valor)";
                            return Objects.equals(v, colVal);
                        }).toList();
                for (int mi = 0; mi < mCount; mi++) {
                    PivotValue pv = values.get(mi);
                    Object a = agg(subset, pv.field(), pv.agg());
                    cells.set(PivotResult.cellIndex(ci, mi, mCount), a);
                    grandTotals.set(PivotResult.cellIndex(ci, mi, mCount),
                            mergeTotals((Number) grandTotals.get(PivotResult.cellIndex(ci, mi, mCount)), (Number) a, pv.agg()));
                }
            }
            rows.add(new PivotRow(e.getKey().stream().map(Object::toString).toList(), cells));
        }

        // Ordenar filas por la primera medida del total global (desc), como una pivot clásica
        if (!rows.isEmpty() && rowFields.size() <= 2) {
            rows.sort((x, y) -> Double.compare(globalTotalOf(y, colGroups.size(), mCount), globalTotalOf(x, colGroups.size(), mCount)));
        }

        return new PivotResult(base, rowFields, colGroups.stream().map(Object::toString).toList(),
                measureLabels, rows, grandTotals);
    }

    private Double globalTotalOf(PivotRow r, int colCount, int mCount) {
        double sum = 0;
        for (Object o : r.cells()) if (o instanceof Number n) sum += n.doubleValue();
        return sum;
    }

    private String aggLabel(String agg, String field) {
        String a = agg == null ? "SUM" : agg.toUpperCase();
        String pretty = switch (a) {
            case "COUNT" -> "Contar";
            case "AVG" -> "Promedio";
            case "MAX" -> "Máximo";
            case "MIN" -> "Mínimo";
            default -> "Suma";
        };
        return pretty + " de " + field;
    }

    /** Agrega {@code rows} para {@code field} según {@code agg}. */
    private Object agg(List<Map<String, Object>> rows, String field, String agg) {
        if (rows.isEmpty()) return null;
        String a = agg == null ? "SUM" : agg.toUpperCase();
        List<Number> nums = rows.stream().map(r -> r.get(field)).filter(v -> v instanceof Number).map(v -> (Number) v).toList();
        if (a.equals("COUNT")) return (double) rows.size();
        if (nums.isEmpty()) return null;
        switch (a) {
            case "AVG" -> {
                double s = 0;
                for (Number n : nums) s += n.doubleValue();
                return s / nums.size();
            }
            case "MAX" -> { double m = Double.NEGATIVE_INFINITY; for (Number n : nums) m = Math.max(m, n.doubleValue()); return m; }
            case "MIN" -> { double m = Double.POSITIVE_INFINITY; for (Number n : nums) m = Math.min(m, n.doubleValue()); return m; }
            default -> { double s = 0; for (Number n : nums) s += n.doubleValue(); return s; }
        }
    }

    /** Combina dos totales parciales para el gran total respetando la agregación. */
    private Object mergeTotals(Number acc, Number v, String agg) {
        if (v == null) return acc;
        if (acc == null) return v.doubleValue();
        String a = agg == null ? "SUM" : agg.toUpperCase();
        if (a.equals("COUNT")) return acc.doubleValue() + v.doubleValue();
        if (a.equals("AVG")) {
            // Mantenemos promediable si las celdas tienen el mismo número de filas; aproximación por suma.
            return acc.doubleValue() + v.doubleValue();
        }
        if (a.equals("MAX")) return Math.max(acc.doubleValue(), v.doubleValue());
        if (a.equals("MIN")) return Math.min(acc.doubleValue(), v.doubleValue());
        return acc.doubleValue() + v.doubleValue();
    }

    // ── Filtros ─────────────────────────────────────────────
    private List<FilterDTO> filtersOf(ReportConfigDTO cfg) {
        return filtersOfChart(cfg.chartConfig());
    }

    private List<FilterDTO> filtersOfChart(Map<String, Object> chartConfig) {
        if (chartConfig == null) return List.of();
        Object f = chartConfig.get("filters");
        if (!(f instanceof List<?> list)) return List.of();
        List<FilterDTO> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Object field = m.get("field");
                if (field == null || String.valueOf(field).isBlank()) continue;
                out.add(new FilterDTO(String.valueOf(field), m.get("op") == null ? "eq" : String.valueOf(m.get("op")), m.get("value")));
            }
        }
        return out;
    }

    private boolean matchesFilters(Map<String, Object> row, List<FilterDTO> filters) {
        for (FilterDTO flt : filters) {
            String key = FormulaEngine.normalizeKey(flt.field());
            Object v = row.get(key);
            String op = flt.op() == null ? "eq" : flt.op();
            switch (op) {
                case "isNull" -> { if (v != null && !"".equals(v)) return false; }
                case "notNull" -> { if (v == null || "".equals(v)) return false; }
                case "eq" -> { if (!equalsValue(v, flt.value())) return false; }
                case "ne" -> { if (equalsValue(v, flt.value())) return false; }
                case "contains" -> { if (!containsValue(v, flt.value())) return false; }
                case "gt" -> { if (compareValue(v, flt.value()) <= 0) return false; }
                case "gte" -> { if (compareValue(v, flt.value()) < 0) return false; }
                case "lt" -> { if (compareValue(v, flt.value()) >= 0) return false; }
                case "lte" -> { if (compareValue(v, flt.value()) > 0) return false; }
                default -> { if (!equalsValue(v, flt.value())) return false; }
            }
        }
        return true;
    }

    private boolean equalsValue(Object v, Object w) {
        if (w == null) return v == null || "".equals(v);
        if (v == null) return "".equals(String.valueOf(w));
        Double a = toNumber(v);
        Double b = toNumber(w);
        if (a != null && b != null) return Double.compare(a, b) == 0;
        if (v instanceof Boolean && w instanceof String) return Boolean.valueOf(String.valueOf(w)).equals(v);
        if (w instanceof Boolean && v instanceof String) return Boolean.valueOf(String.valueOf(v)).equals(w);
        return String.valueOf(v).equalsIgnoreCase(String.valueOf(w));
    }

    private boolean containsValue(Object v, Object w) {
        if (v == null || w == null) return false;
        return String.valueOf(v).toLowerCase().contains(String.valueOf(w).toLowerCase());
    }

    private int compareValue(Object v, Object w) {
        Double a = toNumber(v);
        Double b = toNumber(w);
        if (a != null && b != null) return Double.compare(a, b);
        String sa = v == null ? "" : String.valueOf(v);
        String sb = w == null ? "" : String.valueOf(w);
        return sa.compareToIgnoreCase(sb);
    }

    private static Double toNumber(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) return null;
            try { return Double.valueOf(t); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    // ── Agrupación ──────────────────────────────────────────
    private List<Map<String, Object>> groupBy(List<Map<String, Object>> rawRows, String dim) {
        Map<Object, List<Map<String, Object>>> byDim = rawRows.stream()
                .collect(Collectors.groupingBy(r -> r.get(dim) == null ? "(sin " + dim + ")" : r.get(dim), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Object, List<Map<String, Object>>> e : byDim.entrySet()) {
            List<Map<String, Object>> group = e.getValue();
            Map<String, Object> agg = new LinkedHashMap<>();
            agg.put(dim, strVal(e.getKey()));
            Set<String> keys = new LinkedHashSet<>();
            for (Map<String, Object> r : group) keys.addAll(r.keySet());
            for (String k : keys) {
                if (k.equals(dim)) continue;
                boolean numeric = group.stream().anyMatch(r -> r.get(k) instanceof Number);
                if (numeric) {
                    agg.put(k, aggNumeric(group, k));
                } else {
                    Object first = null;
                    for (Map<String, Object> r : group) if (r.get(k) != null) { first = r.get(k); break; }
                    agg.put(k, first);
                }
            }
            out.add(agg);
        }
        return out;
    }

    /** Suma numérica "inteligente": los campos de nivel ULD/vuelo se suman por entidad distinta. */
    private Double aggNumeric(List<Map<String, Object>> group, String key) {
        boolean uldLevel = ULD_LEVEL_KEYS.contains(key);
        boolean flightLevel = FLIGHT_LEVEL_KEYS.contains(key);
        if (uldLevel || flightLevel) {
            Set<Object> seen = new HashSet<>();
            BigDecimal sum = BigDecimal.ZERO;
            String idKey = uldLevel ? "__uldId" : "__flightId";
            for (Map<String, Object> r : group) {
                Object id = r.get(idKey);
                Object v = r.get(key);
                if (!(v instanceof Number n)) continue;
                if (id == null || seen.add(id)) sum = sum.add(BigDecimal.valueOf(n.doubleValue()));
            }
            return sum.doubleValue();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> r : group) if (r.get(key) instanceof Number n) sum = sum.add(BigDecimal.valueOf(n.doubleValue()));
        return sum.doubleValue();
    }

    private Map<String, BigDecimal> varsFor(Map<String, Object> row, Map<String, BigDecimal> scenario) {
        Map<String, BigDecimal> vars = new HashMap<>();
        row.forEach((k, v) -> {
            if (v instanceof Number n) vars.put(FormulaEngine.normalizeKey(k), BigDecimal.valueOf(n.doubleValue()));
            else if (v instanceof Boolean b) vars.put(FormulaEngine.normalizeKey(k), b ? BigDecimal.ONE : BigDecimal.ZERO);
        });
        vars.putAll(scenario);
        return vars;
    }

    private List<Map<String, Object>> totalsFor(List<Map<String, Object>> rows, Set<String> cols, ReportConfigDTO cfg) {
        // Agregación por fórmula: SUM es el default; respeta el valor elegido en el builder.
        Map<String, String> aggByCol = new HashMap<>();
        if (cfg.formulas() != null) {
            for (CalculatedFieldDTO cf : cfg.formulas()) {
                if (cf.column() == null || cf.column().isBlank()) continue;
                String a = cf.aggregate() == null || cf.aggregate().isBlank() ? "SUM" : cf.aggregate().toUpperCase();
                aggByCol.put(FormulaEngine.normalizeKey(cf.column()), a);
            }
        }
        List<Map<String, Object>> totals = new ArrayList<>();
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("__label", "TOTAL");
        for (String c : cols) {
            List<Number> nums = rows.stream()
                    .map(r -> r.get(c))
                    .filter(v -> v instanceof Number)
                    .map(v -> (Number) v)
                    .toList();
            String agg = aggByCol.getOrDefault(c, "SUM");
            Object val = null;
            switch (agg) {
                case "AVG" -> {
                    double s = nums.stream().mapToDouble(Number::doubleValue).sum();
                    val = nums.isEmpty() ? null : s / nums.size();
                }
                case "MAX" -> val = nums.stream().mapToDouble(Number::doubleValue).max().stream().boxed().findFirst().orElse(null);
                case "MIN" -> val = nums.stream().mapToDouble(Number::doubleValue).min().stream().boxed().findFirst().orElse(null);
                case "COUNT" -> val = rows.size();
                default -> {
                    double s = nums.stream().mapToDouble(Number::doubleValue).sum();
                    val = nums.isEmpty() ? null : s;
                }
            }
            if (val != null) t.put(c, val);
        }
        totals.add(t);
        return totals;
    }

    private static BigDecimal num(BigDecimal v) { return v; }

    private static String strVal(Object o) { return o == null ? null : String.valueOf(o); }

    // ── Tabla base ───────────────────────────────────────────
    private String baseSourceOf(ReportConfigDTO cfg) {
        if (cfg != null && cfg.baseSource() != null && !cfg.baseSource().isBlank()) {
            String b = cfg.baseSource().trim().toLowerCase();
            if (b.equals("mawb") || b.equals("booking") || b.equals("receipt")
                    || b.equals("flight") || b.equals("uld") || b.equals("uld-awb")) return b;
        }
        // Fallback histórico: chartConfig.baseSource
        if (cfg != null && cfg.chartConfig() != null && cfg.chartConfig().get("baseSource") != null) {
            String b = String.valueOf(cfg.chartConfig().get("baseSource")).trim().toLowerCase();
            if (b.equals("mawb") || b.equals("booking") || b.equals("receipt")
                    || b.equals("flight") || b.equals("uld") || b.equals("uld-awb")) return b;
        }
        return "uld-awb";
    }

    /** Construye las filas "crudas" (1 por registro de la tabla base) antes de filtrar/agrupar. */
    private List<Map<String, Object>> buildRawRows(String base, List<MawbEntity> mawbsAll, List<UldAwbEntity> awbs,
                                                   Map<UUID, UldEntity> uldById, Map<UUID, FlightEntity> flightById,
                                                   Map<UUID, AirlineEntity> airlineById, Map<UUID, MawbEntity> mawbById,
                                                   Map<UUID, BookingEntity> bookingByMawbId, Map<String, BookingEntity> bookingByAwb,
                                                   Map<UUID, WarehouseReceiptEntity> receiptByMawbId,
                                                   Map<UUID, List<UldAwbEntity>> awbByUld, Map<UUID, List<UldAwbEntity>> awbByMawb) {
        // Fallback UX: la tabla base por defecto (uld-awb) no tiene filas cuando no hay piezas
        // cargadas en ULDs → se cae a MAWB para que el reporte SIEMPRE muestre data si existen guías.
        if ("uld-awb".equals(base) && awbs.isEmpty() && !mawbsAll.isEmpty()) base = "mawb";
        List<Map<String, Object>> rawRows = new ArrayList<>();
        switch (base) {
            case "mawb" -> {
                for (MawbEntity m : mawbsAll) {
                    BookingEntity b = (m != null && m.getId() != null) ? bookingByMawbId.get(m.getId()) : null;
                    if (b == null && m != null) b = bookingByAwb.get(m.getAwbNumber());
                    WarehouseReceiptEntity r = (m == null || m.getId() == null) ? null : receiptByMawbId.get(m.getId());
                    int pieces = awbByMawb.getOrDefault(m.getId(), List.of()).stream()
                            .mapToInt(a -> a.getPieces() == null ? 0 : a.getPieces()).sum();
                    rawRows.add(buildRowMawb(m, b, r, pieces, flightById, airlineById, uldById));
                }
            }
            case "booking" -> {
                for (BookingEntity b : bookingRepository.findAll()) {
                    MawbEntity m = b.getMawbId() == null ? null : mawbById.get(b.getMawbId());
                    if (m == null) m = (b.getAwbNumber() == null) ? null : mawbById.values().stream()
                            .filter(x -> x.getAwbNumber() != null && x.getAwbNumber().equals(b.getAwbNumber()))
                            .findFirst().orElse(null);
                    WarehouseReceiptEntity r = (m == null || m.getId() == null) ? null : receiptByMawbId.get(m.getId());
                    int pieces = (m == null || m.getId() == null) ? 0 : awbByMawb.getOrDefault(m.getId(), List.of()).stream()
                            .mapToInt(a -> a.getPieces() == null ? 0 : a.getPieces()).sum();
                    rawRows.add(buildRowMawb(m, b, r, pieces, flightById, airlineById, uldById));
                }
            }
            case "receipt" -> {
                for (WarehouseReceiptEntity r : receiptRepository.findAll()) {
                    if (Boolean.TRUE.equals(r.getSuperseded())) continue;
                    MawbEntity m = r.getMawbId() == null ? null : mawbById.get(r.getMawbId());
                    BookingEntity b = m == null ? null : bookingByMawbId.get(m.getId());
                    if (b == null && m != null) b = bookingByAwb.get(m.getAwbNumber());
                    int pieces = (m == null || m.getId() == null) ? 0 : awbByMawb.getOrDefault(m.getId(), List.of()).stream()
                            .mapToInt(a -> a.getPieces() == null ? 0 : a.getPieces()).sum();
                    rawRows.add(buildRowMawb(m, b, r, pieces, flightById, airlineById, uldById));
                }
            }
            case "flight" -> {
                for (FlightEntity f : flightById.values()) {
                    AirlineEntity airline = f.getAirlineId() == null ? null : airlineById.get(f.getAirlineId());
                    rawRows.add(buildRowFlight(f, airline, uldById));
                }
            }
            case "uld" -> {
                for (UldEntity u : uldById.values()) {
                    FlightEntity f = u.getFlightId() == null ? null : flightById.get(u.getFlightId());
                    AirlineEntity airline = null;
                    if (f != null) airline = airlineById.get(f.getAirlineId());
                    if (airline == null && u.getAirlineId() != null) airline = airlineById.get(u.getAirlineId());
                    int pieces = awbByUld.getOrDefault(u.getId(), List.of()).stream()
                            .mapToInt(a -> a.getPieces() == null ? 0 : a.getPieces()).sum();
                    rawRows.add(buildRowUld(u, f, airline, pieces, awbByUld));
                }
            }
            default -> { // uld-awb (comportamiento histórico)
                for (UldAwbEntity aw : awbs) {
                    UldEntity u = aw.getUldId() == null ? null : uldById.get(aw.getUldId());
                    FlightEntity f = (u == null || u.getFlightId() == null) ? null : flightById.get(u.getFlightId());
                    AirlineEntity airline = null;
                    if (f != null) airline = airlineById.get(f.getAirlineId());
                    if (airline == null && u != null && u.getAirlineId() != null) airline = airlineById.get(u.getAirlineId());
                    MawbEntity m = aw.getMawbId() == null ? null : mawbById.get(aw.getMawbId());
                    BookingEntity b = (m != null && m.getId() != null) ? bookingByMawbId.get(m.getId()) : null;
                    if (b == null && m != null) b = bookingByAwb.get(m.getAwbNumber());
                    WarehouseReceiptEntity r = (m == null || m.getId() == null) ? null : receiptByMawbId.get(m.getId());
                    rawRows.add(buildRowAwb(aw, u, f, m, b, r, airline));
                }
            }
        }
        return rawRows;
    }

    /** Fila a partir de un ULD-AWB (grano histórico). */
    private Map<String, Object> buildRowAwb(UldAwbEntity aw, UldEntity u, FlightEntity f, MawbEntity m,
                                            BookingEntity b, WarehouseReceiptEntity r, AirlineEntity airline) {
        Map<String, Object> row = baseFields(u, f, airline);
        row.put("__uldId", u == null ? null : u.getId());
        row.put("__flightId", f == null ? null : f.getId());
        row.put("Pieces", aw.getPieces() == null ? 0 : aw.getPieces());
        fillMawb(row, m);
        fillBooking(row, b);
        fillReceipt(row, r);
        return row;
    }

    /** Fila a partir de un MAWB (1 fila por MAWB; piezas = suma de ULD-AWB). */
    private Map<String, Object> buildRowMawb(MawbEntity m, BookingEntity b, WarehouseReceiptEntity r, int pieces,
                                             Map<UUID, FlightEntity> flightById, Map<UUID, AirlineEntity> airlineById,
                                             Map<UUID, UldEntity> uldById) {
        FlightEntity f = m == null || m.getFlightId() == null ? null : flightById.get(m.getFlightId());
        AirlineEntity airline = null;
        if (f != null) airline = airlineById.get(f.getAirlineId());
        if (airline == null && m != null && m.getAirlineId() != null) airline = airlineById.get(m.getAirlineId());
        Map<String, Object> row = baseFields(null, f, airline);
        row.put("__uldId", null);
        row.put("__flightId", f == null ? null : f.getId());
        row.put("Pieces", pieces);
        fillMawb(row, m);
        fillBooking(row, b);
        fillReceipt(row, r);
        return row;
    }

    /** Fila a partir de un vuelo (1 fila por vuelo). */
    private Map<String, Object> buildRowFlight(FlightEntity f, AirlineEntity airline, Map<UUID, UldEntity> uldById) {
        Map<String, Object> row = baseFields(null, f, airline);
        row.put("__uldId", null);
        row.put("__flightId", f == null ? null : f.getId());
        int totalPieces = 0;
        for (UldEntity u : uldById.values()) {
            if (u.getFlightId() != null && f != null && u.getFlightId().equals(f.getId())) {
                totalPieces += 0;
            }
        }
        row.put("Pieces", totalPieces);
        return row;
    }

    /** Fila a partir de un ULD (1 fila por ULD). */
    private Map<String, Object> buildRowUld(UldEntity u, FlightEntity f, AirlineEntity airline, int pieces,
                                            Map<UUID, List<UldAwbEntity>> awbByUld) {
        Map<String, Object> row = baseFields(u, f, airline);
        row.put("__uldId", u == null ? null : u.getId());
        row.put("__flightId", f == null ? null : f.getId());
        row.put("Pieces", pieces);
        return row;
    }

    /** Columnas comunes de ULD + vuelo + aerolínea. */
    private Map<String, Object> baseFields(UldEntity u, FlightEntity f, AirlineEntity airline) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("UldNumber", u == null ? null : u.getUldNumber());
        row.put("UldType", u == null ? null : u.getUldType());
        row.put("Status", u == null ? null : u.getStatus());
        row.put("Position", u == null ? null : u.getPosition());
        row.put("SealNumber", u == null ? null : u.getSealNumber());
        row.put("TareLbs", num(u == null ? null : u.getTareLbs()));
        row.put("GrossLbs", num(u == null ? null : u.getGrossWeightLbs()));
        row.put("NetLbs", num(u == null ? null : u.getNetWeightLbs()));
        row.put("TareKg", num(u == null ? null : u.getTareKg()));
        row.put("GrossKg", num(u == null ? null : u.getGrossWeightKg()));
        row.put("NetKg", num(u == null ? null : u.getNetWeightKg()));
        row.put("FlightNumber", f == null ? null : f.getFlightNumber());
        row.put("FlightDate", f == null ? null : String.valueOf(f.getFlightDate()));
        row.put("Origin", f == null ? null : f.getOrigin());
        row.put("Destination", f == null ? null : f.getDestination());
        row.put("AircraftReg", f == null ? null : f.getAircraftReg());
        row.put("AircraftType", f == null ? null : f.getAircraftType());
        row.put("FlightStatus", f == null ? null : f.getStatus());
        row.put("MaxPayloadKg", f == null ? null : num(f.getMaxPayloadKg()));
        row.put("AirlineCode", airline == null ? null : airline.getCode());
        row.put("AirlineName", airline == null ? null : airline.getName());
        return row;
    }

    private void fillMawb(Map<String, Object> row, MawbEntity m) {
        row.put("AwbNumber", m == null ? null : m.getAwbNumber());
        row.put("MawbShipper", m == null ? null : m.getShipperName());
        row.put("MawbConsignee", m == null ? null : m.getConsigneeName());
        row.put("MawbOrigin", m == null ? null : m.getOrigin());
        row.put("MawbDestination", m == null ? null : m.getDestination());
        row.put("MawbPieces", m == null ? null : m.getPieces());
        row.put("ReportedWeightKg", m == null ? null : num(m.getReportedWeightKg()));
        row.put("ChargeableWeightKg", m == null ? null : num(m.getChargeableWeightKg()));
        row.put("MawbCommodity", m == null ? null : m.getCommodityType());
        row.put("MawbStatus", m == null ? null : m.getStatus());
        row.put("CashOnly", m == null ? null : m.getCashOnly());
        row.put("MawbPreBuilt", m == null ? null : m.getPreBuilt());
    }

    private void fillBooking(Map<String, Object> row, BookingEntity b) {
        row.put("BookingClient", b == null ? null : b.getClientName());
        row.put("BookingShipper", b == null ? null : b.getShipperName());
        row.put("BookingCnee", b == null ? null : b.getCnee());
        row.put("BookingDestination", b == null ? null : b.getDestination());
        row.put("Skids", b == null ? null : b.getSkids());
        row.put("Units", b == null ? null : b.getUnits());
        row.put("ReservedKg", b == null ? null : num(b.getReservedKg()));
        row.put("ConfirmedKg", b == null ? null : num(b.getConfirmedKg()));
        row.put("ReceivedKg", b == null ? null : num(b.getReceivedKg()));
        row.put("FulfillmentPct", b == null ? null : num(b.getFulfillmentPct()));
        row.put("BookingCommodity", b == null ? null : b.getCommodityType());
        row.put("Priority", b == null ? null : b.getPriority());
        row.put("IsConfirmed", b == null ? null : b.getIsConfirmed());
    }

    private void fillReceipt(Map<String, Object> row, WarehouseReceiptEntity r) {
        row.put("ReceiptPieces", r == null ? null : r.getPieceCount());
        row.put("AwbReportedPieces", r == null ? null : r.getAwbReportedPieces());
        row.put("ActualWeightKg", r == null ? null : num(r.getActualWeightKg()));
        row.put("ChargeableKg", r == null ? null : num(r.getChargeableWeightKg()));
        row.put("ActualWeightLbs", r == null ? null : num(r.getActualWeightLbs()));
        row.put("ChargeableLbs", r == null ? null : num(r.getChargeableWeightLbs()));
        row.put("ReceiptDate", r == null || r.getReceiptDate() == null ? null : String.valueOf(r.getReceiptDate().toLocalDate()));
        row.put("CreatedByName", r == null ? null : r.getCreatedByName());
        row.put("RcptCashOnly", r == null ? null : r.getCashOnly());
        row.put("BookedInAcoms", r == null ? null : r.getBookedInAcoms());
        row.put("DocsProvided", r == null ? null : r.getDocsProvided());
        row.put("CustomsCompleted", r == null ? null : r.getCustomsCompleted());
        row.put("RcptPreBuilt", r == null ? null : r.getPreBuilt());
    }

    // ── Top N ────────────────────────────────────────────────
    private int topNOf(ReportConfigDTO cfg) {
        if (cfg.chartConfig() == null) return 0;
        Object n = cfg.chartConfig().get("topN");
        if (n instanceof Number num) return num.intValue();
        if (n instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private String chartYOf(ReportConfigDTO cfg, List<Map<String, Object>> rows) {
        String prefRendered = null;
        if (cfg.chartConfig() != null && cfg.chartConfig().get("y") != null) {
            prefRendered = FormulaEngine.normalizeKey(String.valueOf(cfg.chartConfig().get("y")));
        }
        final String pref = prefRendered;
        if (pref != null && rows.stream().anyMatch(r -> r.get(pref) instanceof Number)) return pref;
        for (Map<String, Object> r : rows) {
            for (String c : r.keySet()) {
                if (c.startsWith("__")) continue;
                if (r.get(c) instanceof Number) return c;
            }
            break;
        }
        return prefRendered == null ? "" : prefRendered;
    }

    private static double doubleOrZero(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
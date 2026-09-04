package com.aircargo.exportservice.service;

import com.aircargo.exportservice.dto.EvaluateResult;
import com.aircargo.exportservice.dto.FieldDefDTO;
import com.aircargo.exportservice.dto.ReportConfigDTO;
import com.aircargo.exportservice.entity.AirlineEntity;
import com.aircargo.exportservice.entity.BookingEntity;
import com.aircargo.exportservice.entity.FlightEntity;
import com.aircargo.exportservice.entity.MawbEntity;
import com.aircargo.exportservice.entity.UldAwbEntity;
import com.aircargo.exportservice.entity.UldEntity;
import com.aircargo.exportservice.repository.AirlineRepository;
import com.aircargo.exportservice.repository.BookingRepository;
import com.aircargo.exportservice.repository.FlightRepository;
import com.aircargo.exportservice.repository.MawbRepository;
import com.aircargo.exportservice.repository.UldAwbRepository;
import com.aircargo.exportservice.repository.UldRepository;
import com.aircargo.exportservice.repository.WarehouseReceiptRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardBuilderServiceTest {

    private final UldRepository uldRepo = mock(UldRepository.class);
    private final UldAwbRepository uldAwbRepo = mock(UldAwbRepository.class);
    private final FlightRepository flightRepo = mock(FlightRepository.class);
    private final MawbRepository mawbRepo = mock(MawbRepository.class);
    private final BookingRepository bookingRepo = mock(BookingRepository.class);
    private final WarehouseReceiptRepository receiptRepo = mock(WarehouseReceiptRepository.class);
    private final AirlineRepository airlineRepo = mock(AirlineRepository.class);

    private DashboardBuilderService service() {
        return new DashboardBuilderService(uldRepo, uldAwbRepo, flightRepo, mawbRepo, bookingRepo, receiptRepo, airlineRepo);
    }

    // ── Fixtures ────────────────────────────────────────────
    private AirlineEntity airline(String code) {
        AirlineEntity a = new AirlineEntity();
        a.setId(UUID.randomUUID());
        a.setCode(code);
        a.setName("Airline " + code);
        return a;
    }

    // cfg(name, dimension, fieldSources, formulas, scenario, chartConfig, baseSource)
    private ReportConfigDTO cfg(String name, String dim, List<String> sources,
                                List<com.aircargo.exportservice.dto.CalculatedFieldDTO> formulas,
                                Map<String, Object> scenario, Map<String, Object> chart, String base) {
        return new ReportConfigDTO(name, dim, sources, formulas, scenario, chart, base);
    }

    private FlightEntity flight(AirlineEntity a, String number) {
        FlightEntity f = new FlightEntity();
        f.setId(UUID.randomUUID());
        f.setAirlineId(a.getId());
        f.setFlightNumber(number);
        f.setOrigin("SDQ");
        f.setDestination("MIA");
        f.setFlightDate(LocalDate.of(2026, 9, 1));
        f.setStatus("CONFIRMED");
        f.setMaxPayloadKg(new BigDecimal("1200"));
        return f;
    }

    private UldEntity uld(FlightEntity f, String number, double gross, double tare) {
        UldEntity u = new UldEntity();
        u.setId(UUID.randomUUID());
        u.setAirlineId(f.getAirlineId());
        u.setFlightId(f.getId());
        u.setUldNumber(number);
        u.setUldType("PMC");
        u.setStatus("BUILT");
        u.setGrossWeightLbs(BigDecimal.valueOf(gross));
        u.setTareLbs(BigDecimal.valueOf(tare));
        u.setNetWeightLbs(BigDecimal.valueOf(gross - tare));
        return u;
    }

    private UldAwbEntity awb(UldEntity u, MawbEntity m, int pieces) {
        UldAwbEntity aw = new UldAwbEntity();
        aw.setId(UUID.randomUUID());
        aw.setUldId(u.getId());
        aw.setMawbId(m == null ? null : m.getId());
        aw.setPieces(pieces);
        return aw;
    }

    private MawbEntity mawb(AirlineEntity a, String awb, String status, int pieces) {
        MawbEntity m = new MawbEntity();
        m.setId(UUID.randomUUID());
        m.setAirlineId(a.getId());
        m.setFlightId(null);
        m.setAwbNumber(awb);
        m.setShipperName("Shipper " + awb);
        m.setConsigneeName("Consignee " + awb);
        m.setOrigin("SDQ");
        m.setDestination("MIA");
        m.setPieces(pieces);
        m.setCommodityType("GENERAL");
        m.setStatus(status);
        return m;
    }

    // ── Tests ───────────────────────────────────────────────
    @Test
    void fields_catalogsAllTables() {
        List<FieldDefDTO> fs = service().fields();
        assertTrue(fs.size() >= 40);
        for (String source : Arrays.asList("uld", "flight", "airline", "mawb", "booking", "receipt", "scenario")) {
            assertTrue(fs.stream().anyMatch(f -> source.equals(f.source())),
                    "debe haber al menos un campo de la tabla " + source);
        }
        for (FieldDefDTO f : fs) {
            assertNotNull(f.key());
            assertNotNull(f.label());
        }
    }

    @Test
    void evaluate_buildsOneRowPerUldAwbWithJoins() {
        AirlineEntity air = airline("5Y");
        FlightEntity f = flight(air, "5Y1234");
        UldEntity u = uld(f, "PMC1001", 100, 20);
        MawbEntity m = mawb(air, "123-45678901", "RECEIVED", 8);

        when(airlineRepo.findAll()).thenReturn(List.of(air));
        when(uldAwbRepo.findAll()).thenReturn(List.of(awb(u, m, 5), awb(u, m, 3)));
        when(uldRepo.findAll()).thenReturn(List.of(u));
        when(flightRepo.findAll()).thenReturn(List.of(f));
        when(mawbRepo.findAll()).thenReturn(List.of(m));
        when(bookingRepo.findAll()).thenReturn(List.of());
        when(receiptRepo.findAll()).thenReturn(List.of());

        EvaluateResult r = service().evaluate(cfg(null, null, null, List.of(), Map.of(), null, null));

        assertEquals(2, r.rows().size(), "una fila por ULD-AWB");
        Map<String, Object> row = r.rows().get(0);
        assertEquals("PMC1001", row.get("UldNumber"));
        assertEquals("5Y1234", row.get("FlightNumber"));
        assertEquals("123-45678901", row.get("AwbNumber"));
        assertEquals("5Y", row.get("AirlineCode"));
        assertEquals("RECEIVED", row.get("MawbStatus"));
        assertEquals(5, ((Number) row.get("Pieces")).intValue());
        assertTrue(r.columns().contains("FlightNumber"));
    }

    @Test
    void evaluate_appliesFilters() {
        AirlineEntity air = airline("5Y");
        FlightEntity f = flight(air, "5Y1234");
        UldEntity u = uld(f, "PMC1001", 100, 20);
        MawbEntity received = mawb(air, "123-00000001", "RECEIVED", 10);
        MawbEntity booked = mawb(air, "123-00000002", "BOOKED", 10);

        when(airlineRepo.findAll()).thenReturn(List.of());
        when(uldAwbRepo.findAll()).thenReturn(List.of(awb(u, received, 6), awb(u, booked, 4)));
        when(uldRepo.findAll()).thenReturn(List.of(u));
        when(flightRepo.findAll()).thenReturn(List.of(f));
        when(mawbRepo.findAll()).thenReturn(List.of(received, booked));
        when(bookingRepo.findAll()).thenReturn(List.of());
        when(receiptRepo.findAll()).thenReturn(List.of());

        Map<String, Object> chart = Map.of("filters", List.of(
                Map.of("field", "MawbStatus", "op", "eq", "value", "RECEIVED")));
        EvaluateResult r = service().evaluate(cfg(null, null, null, List.of(), Map.of(), chart, null));

        assertEquals(1, r.rows().size());
        assertEquals("123-00000001", r.rows().get(0).get("AwbNumber"));

        // filtro numérico: piezas >= 6
        Map<String, Object> chart2 = Map.of("filters", List.of(
                Map.of("field", "Pieces", "op", "gte", "value", "6")));
        EvaluateResult r2 = service().evaluate(cfg(null, null, null, List.of(), Map.of(), chart2, null));
        assertEquals(1, r2.rows().size());

        // isNull sobre campo sin valor
        Map<String, Object> chart3 = Map.of("filters", List.of(
                Map.of("field", "IsConfirmed", "op", "isNull", "value", "")));
        EvaluateResult r3 = service().evaluate(cfg(null, null, null, List.of(), Map.of(), chart3, null));
        assertEquals(2, r3.rows().size());
        when(bookingRepo.findAll()).thenReturn(List.of());
    }

    @Test
    void evaluate_groupsByDimensionAndSumsUldWeightsByDistinctUld() {
        AirlineEntity air = airline("5Y");
        FlightEntity f1 = flight(air, "5Y1000");
        FlightEntity f2 = flight(air, "5Y2000");
        UldEntity u1 = uld(f1, "PMC1001", 100, 20);
        UldEntity u2 = uld(f2, "PMC1002", 100, 20);
        MawbEntity m1 = mawb(air, "123-00000001", "RECEIVED", 10);
        MawbEntity m2 = mawb(air, "123-00000002", "RECEIVED", 10);

        when(airlineRepo.findAll()).thenReturn(List.of(air));
        when(uldAwbRepo.findAll()).thenReturn(List.of(awb(u1, m1, 5), awb(u2, m2, 5)));
        when(uldRepo.findAll()).thenReturn(List.of(u1, u2));
        when(flightRepo.findAll()).thenReturn(List.of(f1, f2));
        when(mawbRepo.findAll()).thenReturn(List.of(m1, m2));
        when(bookingRepo.findAll()).thenReturn(List.of());
        when(receiptRepo.findAll()).thenReturn(List.of());

        EvaluateResult r = service().evaluate(cfg(null, "AirlineCode", null, List.of(), Map.of(), null, null));

        assertEquals(1, r.rows().size(), "ambos vuelos comparten aerolínea");
        Map<String, Object> g = r.rows().get(0);
        assertEquals(200.0, ((Number) g.get("GrossLbs")).doubleValue(), "gross sumado por ULD distinto, no por fila");
        assertEquals(10.0, ((Number) g.get("Pieces")).doubleValue());
        assertEquals(2400.0, ((Number) g.get("MaxPayloadKg")).doubleValue(), "payload sumado por vuelo distinto");
    }

    @Test
    void evaluate_respectsFieldSourcesColumns() {
        AirlineEntity air = airline("5Y");
        FlightEntity f = flight(air, "5Y1234");
        UldEntity u = uld(f, "PMC1001", 100, 20);
        MawbEntity m = mawb(air, "123-45678901", "RECEIVED", 8);

        when(airlineRepo.findAll()).thenReturn(List.of());
        when(uldAwbRepo.findAll()).thenReturn(List.of(awb(u, m, 5)));
        when(uldRepo.findAll()).thenReturn(List.of(u));
        when(flightRepo.findAll()).thenReturn(List.of(f));
        when(mawbRepo.findAll()).thenReturn(List.of(m));
        when(bookingRepo.findAll()).thenReturn(List.of());
        when(receiptRepo.findAll()).thenReturn(List.of());

        EvaluateResult r = service().evaluate(cfg(null, "AirlineCode",
                List.of("FlightNumber", "Pieces"), List.of(), Map.of(), null, null));

        assertEquals(List.of("AirlineCode", "FlightNumber", "Pieces"), r.columns());
        Map<String, Object> row = r.rows().get(0);
        assertEquals(3, row.size());
        assertEquals("5Y1234", row.get("FlightNumber"));
    }

    @Test
    void evaluate_totalsRespectFormulaAggregation() {
        AirlineEntity air = airline("5Y");
        FlightEntity f = flight(air, "5Y1234");
        UldEntity u = uld(f, "PMC1001", 100, 20);
        MawbEntity m1 = mawb(air, "123-00000001", "RECEIVED", 10);
        MawbEntity m2 = mawb(air, "123-00000002", "RECEIVED", 10);

        when(airlineRepo.findAll()).thenReturn(List.of());
        when(uldAwbRepo.findAll()).thenReturn(List.of(awb(u, m1, 5), awb(u, m2, 5)));
        when(uldRepo.findAll()).thenReturn(List.of(u));
        when(flightRepo.findAll()).thenReturn(List.of(f));
        when(mawbRepo.findAll()).thenReturn(List.of(m1, m2));
        when(bookingRepo.findAll()).thenReturn(List.of());
        when(receiptRepo.findAll()).thenReturn(List.of());

        com.aircargo.exportservice.dto.CalculatedFieldDTO cf =
                new com.aircargo.exportservice.dto.CalculatedFieldDTO("DoublePieces", "[Pieces] * 2", "AVG");
        EvaluateResult r = service().evaluate(cfg(null, null,
                List.of("Pieces", "DoublePieces"), List.of(cf), Map.of(), null, null));

        Map<String, Object> total = r.totals().get(0);
        assertEquals(10.0, ((Number) total.get("Pieces")).doubleValue(), "SUM default de Piezas (5+5)");
        assertEquals(10.0, ((Number) total.get("DoublePieces")).doubleValue(), "AVG de DoublePieces = (10+10)/2");
    }

    @Test
    void evaluate_topNKeepsHighestRows() {
        AirlineEntity air = airline("5Y");
        FlightEntity f1 = flight(air, "5Y1000");
        FlightEntity f2 = flight(air, "5Y2000");
        FlightEntity f3 = flight(air, "5Y3000");
        UldEntity u1 = uld(f1, "PMC1001", 100, 20);
        UldEntity u2 = uld(f2, "PMC1002", 300, 20);
        UldEntity u3 = uld(f3, "PMC1003", 200, 20);
        MawbEntity m1 = mawb(air, "123-00000001", "RECEIVED", 10);
        MawbEntity m2 = mawb(air, "123-00000002", "RECEIVED", 10);
        MawbEntity m3 = mawb(air, "123-00000003", "RECEIVED", 10);

        when(airlineRepo.findAll()).thenReturn(List.of());
        when(uldAwbRepo.findAll()).thenReturn(List.of(awb(u1, m1, 1), awb(u2, m2, 1), awb(u3, m3, 1)));
        when(uldRepo.findAll()).thenReturn(List.of(u1, u2, u3));
        when(flightRepo.findAll()).thenReturn(List.of(f1, f2, f3));
        when(mawbRepo.findAll()).thenReturn(List.of(m1, m2, m3));
        when(bookingRepo.findAll()).thenReturn(List.of());
        when(receiptRepo.findAll()).thenReturn(List.of());

        Map<String, Object> chart = Map.of("topN", 1, "y", "GrossLbs");
        EvaluateResult r = service().evaluate(cfg(null, "FlightNumber", List.of("FlightNumber", "GrossLbs"), List.of(), Map.of(), chart, null));

        assertEquals(1, r.rows().size());
        assertEquals("5Y2000", r.rows().get(0).get("FlightNumber"), "Top 1 = el de mayor GrossLbs");
    }

    @Test
    void evaluate_baseSourceMawbReturnsRowsEvenWhenUldAwbEmpty() {
        AirlineEntity air = airline("5Y");
        FlightEntity f = flight(air, "5Y1000");
        MawbEntity m1 = mawb(air, "123-00000001", "RECEIVED", 8);
        MawbEntity m2 = mawb(air, "123-00000002", "BOOKED", 4);

        when(airlineRepo.findAll()).thenReturn(List.of(air));
        when(uldAwbRepo.findAll()).thenReturn(List.of());           // <-- sin ULD-AWB
        when(uldRepo.findAll()).thenReturn(List.of());
        when(flightRepo.findAll()).thenReturn(List.of(f));
        when(mawbRepo.findAll()).thenReturn(List.of(m1, m2));
        when(bookingRepo.findAll()).thenReturn(List.of());
        when(receiptRepo.findAll()).thenReturn(List.of());

        EvaluateResult r = service().evaluate(cfg(null, null,
                List.of("AwbNumber", "MawbPieces", "Pieces"), List.of(), Map.of(), null, "mawb"));

        assertEquals(2, r.rows().size(), "1 fila por MAWB aunque uld_awb esté vacío");
        assertEquals("123-00000001", r.rows().get(0).get("AwbNumber"));
        assertEquals(0, ((Number) r.rows().get(0).get("Pieces")).intValue(), "sin ULD-AWB las piezas son 0");
        assertEquals(4, ((Number) r.rows().get(1).get("MawbPieces")).intValue(), "MAWB 2 según su propio reporte");
        assertTrue(r.columns().contains("AwbNumber"));
    }

    @Test
    void evaluate_baseSourceBookingGroupsByFlight() {
        AirlineEntity air = airline("5Y");
        FlightEntity f1 = flight(air, "5Y1000");
        FlightEntity f2 = flight(air, "5Y2000");
        MawbEntity m1 = mawb(air, "123-00000001", "RECEIVED", 8);
        m1.setFlightId(f1.getId());
        MawbEntity m2 = mawb(air, "123-00000002", "RECEIVED", 8);
        m2.setFlightId(f2.getId());
        BookingEntity b1 = new BookingEntity();
        b1.setId(UUID.randomUUID());
        b1.setFlightId(f1.getId());
        b1.setAirlineId(air.getId());
        b1.setMawbId(m1.getId());
        b1.setAwbNumber(m1.getAwbNumber());
        b1.setClientName("Cliente A");
        b1.setUnits(10);
        b1.setIsConfirmed(true);
        BookingEntity b2 = new BookingEntity();
        b2.setId(UUID.randomUUID());
        b2.setFlightId(f2.getId());
        b2.setAirlineId(air.getId());
        b2.setMawbId(m2.getId());
        b2.setAwbNumber(m2.getAwbNumber());
        b2.setClientName("Cliente B");
        b2.setUnits(20);
        b2.setIsConfirmed(false);

        when(airlineRepo.findAll()).thenReturn(List.of());
        when(uldAwbRepo.findAll()).thenReturn(List.of());
        when(uldRepo.findAll()).thenReturn(List.of());
        when(flightRepo.findAll()).thenReturn(List.of(f1, f2));
        when(mawbRepo.findAll()).thenReturn(List.of(m1, m2));
        when(bookingRepo.findAll()).thenReturn(List.of(b1, b2));
        when(receiptRepo.findAll()).thenReturn(List.of());

        EvaluateResult r = service().evaluate(cfg(null, "FlightNumber",
                List.of("BookingClient", "Units"), List.of(), Map.of(), null, "booking"));

        assertEquals(2, r.rows().size(), "1 fila por booking, con vuelo resuelto por MAWB·flight");
        Map<String, Object> first = r.rows().stream()
                .filter(x -> "Cliente A".equals(x.get("BookingClient"))).findFirst().orElseThrow();
        assertEquals(10, ((Number) first.get("Units")).intValue());
        assertEquals("5Y1000", first.get("FlightNumber"), "vuelo resuelto desde el MAWB del booking");
    }

    // ── Pivot ───────────────────────────────────────────────
    private com.aircargo.exportservice.dto.PivotConfig pivotCfg(String base, List<String> rows,
                                                                List<com.aircargo.exportservice.dto.PivotValue> values,
                                                                String column, Map<String, Object> chart) {
        return new com.aircargo.exportservice.dto.PivotConfig(base, rows, values, column, chart);
    }

    @Test
    void pivot_groupsByRowDimensionsAndAggregatesMeasures() {
        AirlineEntity air = airline("5Y");
        FlightEntity f1 = flight(air, "5Y1000");
        FlightEntity f2 = flight(air, "5Y2000");
        MawbEntity m1 = mawb(air, "123-00000001", "RECEIVED", 8);
        m1.setFlightId(f1.getId());
        MawbEntity m2 = mawb(air, "123-00000002", "RECEIVED", 8);
        m2.setFlightId(f1.getId());
        MawbEntity m3 = mawb(air, "123-00000003", "BOOKED", 4);
        m3.setFlightId(f2.getId());

        when(airlineRepo.findAll()).thenReturn(List.of(air));
        when(uldAwbRepo.findAll()).thenReturn(List.of());
        when(uldRepo.findAll()).thenReturn(List.of());
        when(flightRepo.findAll()).thenReturn(List.of(f1, f2));
        when(mawbRepo.findAll()).thenReturn(List.of(m1, m2, m3));
        when(bookingRepo.findAll()).thenReturn(List.of());
        when(receiptRepo.findAll()).thenReturn(List.of());

        com.aircargo.exportservice.dto.PivotConfig pc = pivotCfg("mawb",
                List.of("MawbStatus"),
                List.of(new com.aircargo.exportservice.dto.PivotValue("MawbPieces", "SUM")),
                null, null);

        var r = service().pivot(pc);

        assertEquals(List.of("MawbStatus"), r.rowFields());
        assertEquals(List.of("__total"), r.colGroups(), "sin columna pivot → solo total");
        assertEquals(2, r.rows().size(), "agrupa por estado: RECEIVED y BOOKED");
        com.aircargo.exportservice.dto.PivotRow rt = r.rows().stream()
                .filter(x -> x.key().get(0).equals("RECEIVED")).findFirst().orElseThrow();
        assertEquals(16.0, ((Number) rt.cells().get(0)).doubleValue(), "SUM de piezas MAWB en RECEIVED (8+8)");
        com.aircargo.exportservice.dto.PivotRow bt = r.rows().stream()
                .filter(x -> x.key().get(0).equals("BOOKED")).findFirst().orElseThrow();
        assertEquals(4.0, ((Number) bt.cells().get(0)).doubleValue(), "SUM de piezas MAWB en BOOKED (4)");
    }

    @Test
    void pivot_columnPivotCreatesColumnGroups() {
        AirlineEntity air = airline("5Y");
        FlightEntity f1 = flight(air, "5Y1000");
        FlightEntity f2 = flight(air, "5Y2000");
        MawbEntity m1 = mawb(air, "123-00000001", "RECEIVED", 8);
        m1.setFlightId(f1.getId());
        MawbEntity m2 = mawb(air, "123-00000002", "BOOKED", 4);
        m2.setFlightId(f1.getId());
        MawbEntity m3 = mawb(air, "123-00000003", "RECEIVED", 6);
        m3.setFlightId(f2.getId());

        when(airlineRepo.findAll()).thenReturn(List.of(air));
        when(uldAwbRepo.findAll()).thenReturn(List.of());
        when(uldRepo.findAll()).thenReturn(List.of());
        when(flightRepo.findAll()).thenReturn(List.of(f1, f2));
        when(mawbRepo.findAll()).thenReturn(List.of(m1, m2, m3));
        when(bookingRepo.findAll()).thenReturn(List.of());
        when(receiptRepo.findAll()).thenReturn(List.of());

        com.aircargo.exportservice.dto.PivotConfig pc = pivotCfg("mawb",
                List.of("FlightNumber"),
                List.of(new com.aircargo.exportservice.dto.PivotValue("MawbPieces", "SUM")),
                "MawbStatus", null);

        var r = service().pivot(pc);

        assertTrue(r.colGroups().contains("RECEIVED"));
        assertTrue(r.colGroups().contains("BOOKED"));
        // mCount=1, índice = colIdx*1
        com.aircargo.exportservice.dto.PivotRow row = r.rows().stream()
                .filter(x -> x.key().get(0).equals("5Y1000")).findFirst().orElseThrow();
        int receivedIdx = r.colGroups().indexOf("RECEIVED");
        int bookedIdx = r.colGroups().indexOf("BOOKED");
        assertEquals(8.0, ((Number) row.cells().get(receivedIdx)).doubleValue(), "RECEIVED de 5Y1000 (8)");
        assertEquals(4.0, ((Number) row.cells().get(bookedIdx)).doubleValue(), "BOOKED de 5Y1000 (4)");
        com.aircargo.exportservice.dto.PivotRow row2 = r.rows().stream()
                .filter(x -> x.key().get(0).equals("5Y2000")).findFirst().orElseThrow();
        assertEquals(6.0, ((Number) row2.cells().get(receivedIdx)).doubleValue(), "RECEIVED de 5Y2000 (6)");
    }
}
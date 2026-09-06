package com.aircargo.loadplanningservice.service;

import com.aircargo.feign.client.FlightClient;
import com.aircargo.feign.client.UldClient;
import com.aircargo.feign.dto.AirlineDTO;
import com.aircargo.feign.dto.FlightDTO;
import com.aircargo.feign.dto.UldDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class LoadPlanningExportService {

    private final UldClient uldClient;
    private final FlightClient flightClient;

    public LoadPlanningExportService(UldClient uldClient, FlightClient flightClient) {
        this.uldClient = uldClient;
        this.flightClient = flightClient;
    }

    public ByteArrayInputStream exportFlightLoadPlan(UUID flightId) throws Exception {
        List<UldDTO> ulds = uldClient.getUlds(null, flightId);
        FlightDTO flight = flightClient.getFlightById(flightId);
        AirlineDTO airline = resolveAirline(flight);

        String[] columns = {"ULD NUMBER", "TYPE", "POSITION", "CONFIG", "SEAL #", "TARE (LBS)", "GROSS WT (LBS)", "STATUS"};

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("MANIFIESTO_ESTIBA");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 10);

            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);
            headerCellStyle.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
            headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerCellStyle.setAlignment(HorizontalAlignment.CENTER);
            headerCellStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle dataCellStyle = workbook.createCellStyle();
            dataCellStyle.setBorderBottom(BorderStyle.THIN);
            dataCellStyle.setBorderLeft(BorderStyle.THIN);
            dataCellStyle.setBorderRight(BorderStyle.THIN);
            dataCellStyle.setBorderTop(BorderStyle.THIN);

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 12);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            Font infoFont = workbook.createFont();
            infoFont.setBold(true);
            infoFont.setColor(IndexedColors.WHITE.getIndex());
            infoFont.setFontHeightInPoints((short) 9);

            CellStyle infoStyle = workbook.createCellStyle();
            infoStyle.setFont(infoFont);
            infoStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            infoStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            infoStyle.setAlignment(HorizontalAlignment.LEFT);

            // ── Bloque de cabecera: vuelo / aerolínea / fecha ──
            String flightLabel = joinCodeNum(airline, flight);
            String route = (flight != null && flight.getOrigin() != null ? flight.getOrigin() : "")
                    + " > " + (flight != null && flight.getDestination() != null ? flight.getDestination() : "");

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("MANIFIESTO DE ESTIBA / LOAD PLAN");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columns.length - 1));

            Row infoRow = sheet.createRow(1);
            infoRow.createCell(0).setCellValue("VUELO: " + flightLabel);
            infoRow.getCell(0).setCellStyle(infoStyle);
            String date = flight != null && flight.getFlightDate() != null ? flight.getFlightDate().toString() : "";
            infoRow.createCell(3).setCellValue("FECHA: " + date);
            infoRow.getCell(3).setCellStyle(infoStyle);
            infoRow.createCell(5).setCellValue("RUTA: " + route);
            infoRow.getCell(5).setCellStyle(infoStyle);
            infoRow.createCell(7).setCellValue("AEROLINEA: " + (airline != null ? airline.getCode() : ""));
            infoRow.getCell(7).setCellStyle(infoStyle);

            Row headerRow = sheet.createRow(3);
            for (int col = 0; col < columns.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(columns[col]);
                cell.setCellStyle(headerCellStyle);
            }

            int rowIndex = 4;
            for (UldDTO uld : ulds) {
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(uld.getUldNumber() != null ? uld.getUldNumber() : "");
                row.getCell(0).setCellStyle(dataCellStyle);

                row.createCell(1).setCellValue(uld.getUldType() != null ? uld.getUldType() : "");
                row.getCell(1).setCellStyle(dataCellStyle);

                row.createCell(2).setCellValue(uld.getPosition() != null ? uld.getPosition() : "W/O");
                row.getCell(2).setCellStyle(dataCellStyle);

                row.createCell(3).setCellValue(uld.getConfig() != null ? uld.getConfig() : "");
                row.getCell(3).setCellStyle(dataCellStyle);

                row.createCell(4).setCellValue(uld.getSealNumber() != null ? uld.getSealNumber() : "-");
                row.getCell(4).setCellStyle(dataCellStyle);

                row.createCell(5).setCellValue(uld.getTareLbs() != null ? uld.getTareLbs().doubleValue() : 0.0);
                row.getCell(5).setCellStyle(dataCellStyle);

                row.createCell(6).setCellValue(uld.getGrossWeightLbs() != null ? uld.getGrossWeightLbs().doubleValue() : 0.0);
                row.getCell(6).setCellStyle(dataCellStyle);

                row.createCell(7).setCellValue(uld.getStatus() != null ? uld.getStatus() : "OPEN");
                row.getCell(7).setCellStyle(dataCellStyle);
            }

            for (int col = 0; col < columns.length; col++) {
                sheet.autoSizeColumn(col);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public ByteArrayInputStream exportFlightLoadPlanCsv(UUID flightId) {
        List<UldDTO> ulds = uldClient.getUlds(null, flightId);
        FlightDTO flight = flightClient.getFlightById(flightId);
        AirlineDTO airline = resolveAirline(flight);

        String flightLabel = joinCodeNum(airline, flight);
        String route = (flight != null && flight.getOrigin() != null ? flight.getOrigin() : "")
                + " > " + (flight != null && flight.getDestination() != null ? flight.getDestination() : "");
        String date = flight != null && flight.getFlightDate() != null ? flight.getFlightDate().toString() : "";
        String airlineCode = airline != null ? airline.getCode() : "";

        StringBuilder sb = new StringBuilder();
        sb.append(csvLine(new String[]{
                "MANIFIESTO DE ESTIBA / LOAD PLAN - " + flightLabel
        }));
        sb.append(csvLine(new String[]{
                "Vuelo: " + flightLabel,
                "Fecha: " + date,
                "Ruta: " + route,
                "Aerolinea: " + airlineCode
        }));
        sb.append("\n");
        sb.append(csvLine(new String[]{
                "ULD NUMBER", "TYPE", "POSITION", "CONFIG", "SEAL #", "TARE (LBS)", "GROSS WT (LBS)", "STATUS"
        }));

        for (UldDTO uld : ulds) {
            sb.append(csvLine(rowOf(uld)));
        }

        return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String[] rowOf(UldDTO uld) {
        return new String[]{
                uld.getUldNumber() != null ? uld.getUldNumber() : "",
                uld.getUldType() != null ? uld.getUldType() : "",
                uld.getPosition() != null ? uld.getPosition() : "W/O",
                uld.getConfig() != null ? uld.getConfig() : "",
                uld.getSealNumber() != null ? uld.getSealNumber() : "-",
                doubleStr(uld.getTareLbs()),
                doubleStr(uld.getGrossWeightLbs()),
                uld.getStatus() != null ? uld.getStatus() : "OPEN",
        };
    }

    private static String doubleStr(java.math.BigDecimal v) {
        return v != null ? v.stripTrailingZeros().toPlainString() : "0";
    }

    private static String csvLine(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            String v = fields[i] == null ? "" : fields[i];
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        return sb.append('\n').toString();
    }

    private AirlineDTO resolveAirline(FlightDTO flight) {
        if (flight == null) return null;
        UUID airlineId = flight.getAirlineId();
        if (airlineId == null) return null;
        try {
            return flightClient.getAirlineById(airlineId);
        } catch (Exception ex) {
            return null;
        }
    }

    private String joinCodeNum(AirlineDTO airline, FlightDTO flight) {
        String code = airline != null && airline.getCode() != null ? airline.getCode() : "";
        String num = flight != null && flight.getFlightNumber() != null ? flight.getFlightNumber() : "";
        if (code.isEmpty()) return num;
        if (num.isEmpty()) return code;
        return code + "-" + num;
    }
}
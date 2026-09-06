package com.aircargo.loadplanningservice.service;

import com.aircargo.common.entity.CommodityType;
import com.aircargo.feign.client.*;
import com.aircargo.feign.dto.*;
import com.aircargo.loadplanningservice.dto.LoadPlanningBatchImportResultDTO;
import com.aircargo.loadplanningservice.dto.LoadPlanningSheetImportResultDTO;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class LoadPlanningBatchImportService {

    private static final Pattern AWB_PATTERN = Pattern.compile("^\\d{3}-\\d{8}$");
    private static final Pattern TYPE_PREFIX_PATTERN = Pattern.compile("^[A-Z]{2,5}");

    private final FlightClient flightClient;
    private final UldClient uldClient;
    private final MawbClient mawbClient;
    private final BookingClient bookingClient;

    public LoadPlanningBatchImportService(FlightClient flightClient,
                                           UldClient uldClient,
                                           MawbClient mawbClient,
                                           BookingClient bookingClient) {
        this.flightClient = flightClient;
        this.uldClient = uldClient;
        this.mawbClient = mawbClient;
        this.bookingClient = bookingClient;
    }

    public LoadPlanningBatchImportResultDTO importBatch(MultipartFile file) throws IOException {
        List<LoadPlanningSheetImportResultDTO> sheetResults = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            int totalSheets = workbook.getNumberOfSheets();

            for (int s = 0; s < totalSheets; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                sheetResults.add(importSheet(sheet, sheetName));
            }
        }

        int successSheets = (int) sheetResults.stream().filter(LoadPlanningSheetImportResultDTO::isSuccess).count();
        int failedSheets = sheetResults.size() - successSheets;

        LoadPlanningBatchImportResultDTO result = new LoadPlanningBatchImportResultDTO();
        result.setTotalSheets(sheetResults.size());
        result.setSuccessSheets(successSheets);
        result.setFailedSheets(failedSheets);
        result.setTotalUldsCreated(sheetResults.stream().mapToInt(LoadPlanningSheetImportResultDTO::getUldsCreated).sum());
        result.setTotalUldsUpdated(sheetResults.stream().mapToInt(LoadPlanningSheetImportResultDTO::getUldsUpdated).sum());
        result.setTotalMawbsCreated(sheetResults.stream().mapToInt(LoadPlanningSheetImportResultDTO::getMawbsCreated).sum());
        result.setTotalBookingsCreated(sheetResults.stream().mapToInt(LoadPlanningSheetImportResultDTO::getBookingsCreated).sum());
        result.setTotalUldAwbsCreated(sheetResults.stream().mapToInt(LoadPlanningSheetImportResultDTO::getUldAwbsCreated).sum());
        result.setSheetResults(sheetResults);
        return result;
    }

    private LoadPlanningSheetImportResultDTO importSheet(Sheet sheet, String sheetName) {
        List<String> warnings = new ArrayList<>();
        int uldsCreated = 0, uldsUpdated = 0, mawbsCreated = 0, bookingsCreated = 0, uldAwbsCreated = 0;

        try {
            Row headerRow = sheet.getRow(2);
            if (headerRow == null) {
                return fail(sheetName, "Fila de encabezado (fila 3) no encontrada en la hoja '" + sheetName + "'");
            }

            String flightNumber = getStringValue(headerRow.getCell(4));
            LocalDate flightDate = getDateValue(headerRow.getCell(11));

            if (flightNumber == null || flightNumber.isBlank()) {
                return fail(sheetName, "No se pudo leer el numero de vuelo en E3 de la hoja '" + sheetName + "'");
            }
            if (flightDate == null) {
                return fail(sheetName, "No se pudo leer la fecha del vuelo en L3 de la hoja '" + sheetName + "'");
            }

            FlightDTO flight = flightClient.getAllFlights().stream()
                    .filter(f -> flightNumber.trim().equalsIgnoreCase(f.getFlightNumber().trim())
                            && flightDate.equals(f.getFlightDate()))
                    .findFirst()
                    .orElse(null);

            if (flight == null) {
                return fail(sheetName, "No existe un vuelo " + flightNumber + " con fecha " + flightDate
                        + " (hoja '" + sheetName + "'). Crea el vuelo primero.");
            }

            UUID flightId = flight.getId();
            UldDTO currentUld = null;

            for (int rowIdx = 7; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String uldNumber = getStringValue(row.getCell(1));
                String pcsStr = getStringValue(row.getCell(2));
                String pctStr = getStringValue(row.getCell(3));
                BigDecimal grossLbs = getNumericValue(row.getCell(4));
                BigDecimal tareLbs = getNumericValue(row.getCell(5));
                String config = getStringValue(row.getCell(6));
                String seal = getStringValue(row.getCell(7));
                String position = getStringValue(row.getCell(8));
                String description = getStringValue(row.getCell(9));
                String guia = getStringValue(row.getCell(10));
                String dest = getStringValue(row.getCell(11));

                if ((guia == null || guia.isBlank()) && (dest == null || dest.isBlank())) {
                    continue;
                }

                if (uldNumber != null && !uldNumber.isBlank()) {
                    String normalized = normalizeUldNumber(uldNumber);
                    List<UldDTO> existingUlds = uldClient.getUlds(null, flightId);
                    UldDTO uld = existingUlds.stream()
                            .filter(u -> normalized.equals(normalizeUldNumber(u.getUldNumber())))
                            .findFirst().orElse(null);

                    boolean isNew = (uld == null);
                    if (isNew) {
                        UldDTO createDto = new UldDTO();
                        createDto.setAirlineId(flight.getAirlineId());
                        createDto.setFlightId(flightId);
                        createDto.setUldNumber(normalized);
                        createDto.setUldType(detectUldType(normalized));
                        createDto.setSkipOperatorValidation(true);
                        if (position != null) createDto.setPosition(position);
                        if (config != null) createDto.setConfig(config);
                        if (seal != null) createDto.setSealNumber(seal);
                        if (tareLbs != null) createDto.setTareLbs(tareLbs);
                        if (grossLbs != null) createDto.setGrossWeightLbs(grossLbs);
                        createDto.setStatus("LOADED");
                        uld = uldClient.createUld(createDto);
                        uldsCreated++;
                    } else {
                        UldDTO updateDto = new UldDTO();
                        updateDto.setSkipOperatorValidation(true);
                        if (position != null) updateDto.setPosition(position);
                        if (config != null) updateDto.setConfig(config);
                        if (seal != null) updateDto.setSealNumber(seal);
                        if (tareLbs != null) updateDto.setTareLbs(tareLbs);
                        if (grossLbs != null) updateDto.setGrossWeightLbs(grossLbs);
                        updateDto.setStatus("LOADED");
                        uld = uldClient.updateUld(uld.getId(), updateDto);
                        uldsUpdated++;
                    }
                    currentUld = uld;
                }

                if (currentUld == null) {
                    warnings.add("Fila " + (rowIdx + 1) + ": guia '" + guia
                            + "' sin ULD asociado. Se omite.");
                    continue;
                }

                Integer pieces = parseIntOrNull(pcsStr);
                Integer piecesPct = parseIntOrNull(pctStr);

                MawbDTO mawb = null;
                String mawbLabel = null;

                if (guia != null && AWB_PATTERN.matcher(guia.trim()).matches()) {
                    String awbNumber = guia.trim();
                    try {
                        mawb = mawbClient.getMawbByAwbNumber(awbNumber);
                    } catch (Exception e) {
                        mawb = null;
                    }

                    if (mawb == null) {
                        MawbDTO createDto = new MawbDTO();
                        createDto.setAirlineId(flight.getAirlineId());
                        createDto.setFlightId(flightId);
                        createDto.setAwbNumber(awbNumber);
                        createDto.setOrigin(flight.getOrigin());
                        createDto.setDestination(dest != null ? dest : flight.getDestination());
                        createDto.setPieces(pieces != null ? pieces : 1);
                        createDto.setStatus("ARRIVED");
                        mawb = mawbClient.createMawb(createDto);
                        mawbsCreated++;

                        warnings.add("MAWB " + awbNumber
                                + " no existia y fue creado desde load planning. "
                                + "Verificar shipper/consignee.");
                    }

                    List<BookingDTO> existingBookings = bookingClient.getBookingsByFlight(flightId);
                    boolean bookingExists = existingBookings.stream()
                            .anyMatch(b -> awbNumber.equals(b.getAwbNumber()));

                    if (!bookingExists) {
                        BookingDTO createBooking = new BookingDTO();
                        createBooking.setAirlineId(flight.getAirlineId());
                        createBooking.setFlightId(flightId);
                        createBooking.setMawbId(mawb.getId());
                        createBooking.setAwbNumber(awbNumber);
                        createBooking.setDestination(dest);
                        createBooking.setClientName("PENDIENTE");
                        createBooking.setContactName("PENDIENTE");
                        bookingClient.createBooking(createBooking);
                        bookingsCreated++;

                        warnings.add("Booking creado para AWB " + awbNumber
                                + " (no existia reserva para vuelo " + flightNumber + ").");
                    }
                } else {
                    mawbLabel = guia;
                }

                CommodityType commodityType = mapCommodityType(description);
                UldAwbDTO uldAwbDto = new UldAwbDTO();
                uldAwbDto.setUldId(currentUld.getId());
                if (mawb != null) uldAwbDto.setMawbId(mawb.getId());
                uldAwbDto.setMawbLabel(mawbLabel);
                uldAwbDto.setDescription(commodityType);
                uldAwbDto.setDestination(dest);
                uldAwbDto.setPieces(pieces);
                uldAwbDto.setPiecesPct(piecesPct);
                uldClient.createUldAwb(uldAwbDto);
                uldAwbsCreated++;
            }

            LoadPlanningSheetImportResultDTO result = new LoadPlanningSheetImportResultDTO();
            result.setSheetName(sheetName);
            result.setFlightId(flightId);
            result.setFlightNumber(flightNumber);
            result.setSuccess(true);
            result.setUldsCreated(uldsCreated);
            result.setUldsUpdated(uldsUpdated);
            result.setMawbsCreated(mawbsCreated);
            result.setBookingsCreated(bookingsCreated);
            result.setUldAwbsCreated(uldAwbsCreated);
            result.setWarnings(warnings);
            return result;

        } catch (Exception e) {
            return fail(sheetName, "Error procesando hoja '" + sheetName + "': " + e.getMessage());
        }
    }

    private LoadPlanningSheetImportResultDTO fail(String sheetName, String error) {
        LoadPlanningSheetImportResultDTO result = new LoadPlanningSheetImportResultDTO();
        result.setSheetName(sheetName);
        result.setSuccess(false);
        result.setError(error);
        return result;
    }

    private String getStringValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                String s = cell.getStringCellValue().trim();
                return s.isEmpty() ? null : s;
            case NUMERIC:
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d)) return String.valueOf((long) d);
                return String.valueOf(d);
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }

    private BigDecimal getNumericValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue().trim();
                if (s.isEmpty()) return null;
                return new BigDecimal(s);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private LocalDate getDateValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double v = cell.getNumericCellValue();
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                }
                if (v > 20000 && v < 80000) {
                    return DateUtil.getJavaDate(v).toInstant()
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                }
                return null;
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue().trim();
                return LocalDate.parse(s);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeUldNumber(String uldNumber) {
        if (uldNumber == null) return null;
        return uldNumber.trim().toUpperCase(java.util.Locale.ROOT).replace("-", "");
    }

    private String detectUldType(String uldNumber) {
        if (uldNumber == null) return "BULK";
        String norm = normalizeUldNumber(uldNumber);
        java.util.regex.Matcher matcher = TYPE_PREFIX_PATTERN.matcher(norm);
        if (matcher.find()) {
            return matcher.group();
        }
        return "BULK";
    }

    private CommodityType mapCommodityType(String description) {
        if (description == null || description.isBlank()) {
            return CommodityType.GENERAL;
        }
        String normalized = description.trim().toUpperCase(java.util.Locale.ROOT).replace(" ", "_").replace("-", "_");
        try {
            return CommodityType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return CommodityType.GENERAL;
        }
    }
}

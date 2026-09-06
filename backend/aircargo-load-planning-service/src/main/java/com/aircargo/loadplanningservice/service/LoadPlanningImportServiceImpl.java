package com.aircargo.loadplanningservice.service;

import com.aircargo.common.entity.CommodityType;
import com.aircargo.feign.client.*;
import com.aircargo.feign.dto.*;
import com.aircargo.loadplanningservice.dto.LoadPlanningImportResultDTO;
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
public class LoadPlanningImportServiceImpl implements LoadPlanningImportService {

    private static final Pattern AWB_PATTERN = Pattern.compile("^\\d{3}-\\d{8}$");

    private final FlightClient flightClient;
    private final UldClient uldClient;
    private final MawbClient mawbClient;
    private final BookingClient bookingClient;

    public LoadPlanningImportServiceImpl(FlightClient flightClient,
                                          UldClient uldClient,
                                          MawbClient mawbClient,
                                          BookingClient bookingClient) {
        this.flightClient = flightClient;
        this.uldClient = uldClient;
        this.mawbClient = mawbClient;
        this.bookingClient = bookingClient;
    }

    @Override
    public LoadPlanningImportResultDTO importLoadPlanning(MultipartFile file) throws IOException {
        List<String> warnings = new ArrayList<>();
        int uldsCreated = 0, uldsUpdated = 0, mawbsCreated = 0, bookingsCreated = 0, uldAwbsCreated = 0;

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(2);
            String flightNumber = getStringValue(headerRow.getCell(4));
            LocalDate flightDate = getDateValue(headerRow.getCell(11));

            if (flightNumber == null || flightNumber.isBlank()) {
                throw new IllegalArgumentException("No se pudo leer el numero de vuelo en E3");
            }
            if (flightDate == null) {
                throw new IllegalArgumentException("No se pudo leer la fecha del vuelo en L3");
            }

            FlightDTO flight = flightClient.getAllFlights().stream()
                    .filter(f -> flightNumber.trim().equalsIgnoreCase(f.getFlightNumber().trim())
                            && flightDate.equals(f.getFlightDate()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe un vuelo " + flightNumber + " con fecha " + flightDate
                                    + ". Crea el vuelo primero antes de importar el load planning."));

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
                    List<UldDTO> existingUlds = uldClient.getUlds(null, flightId);
                    UldDTO uld = existingUlds.stream()
                            .filter(u -> uldNumber.equals(u.getUldNumber()))
                            .findFirst().orElse(null);

                    boolean isNew = (uld == null);
                    if (isNew) {
                        UldDTO createDto = new UldDTO();
                        createDto.setAirlineId(flight.getAirlineId());
                        createDto.setFlightId(flightId);
                        createDto.setUldNumber(uldNumber);
                        createDto.setUldType(detectUldType(uldNumber));
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
                            + "' sin ULD asociado (no se pudo determinar el ULD actual). Se omite.");
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
                                + " no existia y fue creado automaticamente desde el load planning. "
                                + "Verificar shipper/consignee y recibo de almacen.");
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

                        warnings.add("Booking creado automaticamente para AWB " + awbNumber
                                + " (no existia reserva previa para el vuelo " + flightNumber + ").");
                    }
                } else {
                    mawbLabel = guia;
                }

                UldAwbDTO uldAwbDto = new UldAwbDTO();
                uldAwbDto.setUldId(currentUld.getId());
                if (mawb != null) uldAwbDto.setMawbId(mawb.getId());
                uldAwbDto.setMawbLabel(mawbLabel);
                uldAwbDto.setDescription(mapCommodityType(description));
                uldAwbDto.setDestination(dest);
                uldAwbDto.setPieces(pieces);
                uldAwbDto.setPiecesPct(piecesPct);
                uldClient.createUldAwb(uldAwbDto);
                uldAwbsCreated++;
            }

            LoadPlanningImportResultDTO result = new LoadPlanningImportResultDTO();
            result.setFlightId(flightId);
            result.setFlightNumber(flight.getFlightNumber());
            result.setUldsCreated(uldsCreated);
            result.setUldsUpdated(uldsUpdated);
            result.setMawbsCreated(mawbsCreated);
            result.setBookingsCreated(bookingsCreated);
            result.setUldAwbsCreated(uldAwbsCreated);
            result.setWarnings(warnings);
            return result;
        }
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
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                LocalDateTime ldt = cell.getLocalDateTimeCellValue();
                return ldt.toLocalDate();
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

    private String detectUldType(String uldNumber) {
        if (uldNumber == null) return "BULK";
        String prefix = uldNumber.split("-")[0].toUpperCase(java.util.Locale.ROOT);
        try {
            return prefix;
        } catch (IllegalArgumentException e) {
            return "BULK";
        }
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

package com.aircargo.loadplanningservice.service;

import com.aircargo.feign.dto.UldDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

@Service
public class RampManifestParserService {

    private static final String KIND_ULD = "uld";
    private static final String KIND_MAWB = "mawb";
    private static final String KIND_GROSS = "gross";
    private static final String KIND_TARE = "tare";
    private static final String KIND_CONFIG = "config";
    private static final String KIND_SEAL = "seal";
    private static final String KIND_POS = "pos";

    private static final Set<String> ULD_ALIASES = normSet("uld", "uldnumber", "uldno", "uldnum", "nould");
    private static final Set<String> MAWB_ALIASES = normSet("noguia", "guia", "mawb", "nawb", "awb", "awbnumber", "mawbnumber", "mawnumber");
    private static final Set<String> GROSS_ALIASES = normSet("peso", "pesobruto", "bruto", "gross", "grosswt", "grossweight", "grosslbs");
    private static final Set<String> TARE_ALIASES = normSet("tara", "tare", "tarelbs", "pesotara", "tareweight", "taralbs");
    private static final Set<String> CONFIG_ALIASES = normSet("config", "type", "tipo", "configtype", "configuracion");
    private static final Set<String> SEAL_ALIASES = normSet("sello", "seal", "sellono", "sealno", "no.sello", "sellonum");
    private static final Set<String> POS_ALIASES = normSet("pos", "position", "posicion");

    public List<UldDTO> parseExcelToNativeUld(MultipartFile file, UUID flightId, UUID airlineId) throws Exception {
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            return parseCsv(file, flightId, airlineId);
        }
        return parseXlsx(file, flightId, airlineId);
    }

    private List<UldDTO> parseXlsx(MultipartFile file, UUID flightId, UUID airlineId) throws Exception {
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = findHeaderRow(sheet);
            if (headerRow == null) {
                throw new IllegalArgumentException("No se pudo localizar la fila de encabezados (columna ULD).");
            }

            Map<String, Integer> cols = mapColumns(headerRow);
            int uldCol = colOf(cols, KIND_ULD);
            if (uldCol < 0) {
                throw new IllegalArgumentException("No se pudo localizar la columna ULD en el encabezado.");
            }
            int mawbCol = colOf(cols, KIND_MAWB);
            int grossCol = colOf(cols, KIND_GROSS);
            int tareCol = colOf(cols, KIND_TARE);
            int configCol = colOf(cols, KIND_CONFIG);
            int sealCol = colOf(cols, KIND_SEAL);
            int posCol = colOf(cols, KIND_POS);

            Map<String, UldDTO> uldMap = new LinkedHashMap<>();
            for (int i = headerRow.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String uld = getCellValueResolved(sheet, row.getCell(uldCol));
                String mawb = mawbCol >= 0 ? getCellValueResolved(sheet, row.getCell(mawbCol)) : "";
                if (isBlank(uld) && isBlank(mawb)) continue;

                addUld(uldMap, uld,
                        grossCol >= 0 ? getCellValueResolved(sheet, row.getCell(grossCol)) : "",
                        tareCol >= 0 ? getCellValueResolved(sheet, row.getCell(tareCol)) : "",
                        configCol >= 0 ? getCellValueResolved(sheet, row.getCell(configCol)) : "",
                        sealCol >= 0 ? getCellValueResolved(sheet, row.getCell(sealCol)) : "",
                        posCol >= 0 ? getCellValueResolved(sheet, row.getCell(posCol)) : "",
                        flightId, airlineId);
            }
            return new ArrayList<>(uldMap.values());
        }
    }

    private List<UldDTO> parseCsv(MultipartFile file, UUID flightId, UUID airlineId) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(splitCsvLine(line));
            }
        }

        int headerIdx = findHeaderIndex(rows);
        if (headerIdx < 0) {
            throw new IllegalArgumentException("No se pudo localizar la fila de encabezados (columna ULD).");
        }

        Map<String, Integer> cols = mapColumns(rows.get(headerIdx));
        int uldCol = colOf(cols, KIND_ULD);
        if (uldCol < 0) {
            throw new IllegalArgumentException("No se pudo localizar la columna ULD en el encabezado.");
        }

        Map<String, UldDTO> uldMap = new LinkedHashMap<>();
        for (int i = headerIdx + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String uld = valueAt(row, uldCol);
            String mawb = valueAt(row, colOf(cols, KIND_MAWB));
            if (isBlank(uld) && isBlank(mawb)) continue;

            addUld(uldMap, uld,
                    valueAt(row, colOf(cols, KIND_GROSS)),
                    valueAt(row, colOf(cols, KIND_TARE)),
                    valueAt(row, colOf(cols, KIND_CONFIG)),
                    valueAt(row, colOf(cols, KIND_SEAL)),
                    valueAt(row, colOf(cols, KIND_POS)),
                    flightId, airlineId);
        }
        return new ArrayList<>(uldMap.values());
    }

    private void addUld(Map<String, UldDTO> uldMap, String uldRaw,
                        String grossRaw, String tareRaw, String configRaw, String sealRaw, String posRaw,
                        UUID flightId, UUID airlineId) {
        if (isBlank(uldRaw)) return;
        String uld = uldRaw.trim().toUpperCase(Locale.ROOT);
        if (uldMap.containsKey(uld)) return;

        String type = detectUldType(uld);
        String config = cleanConfig(configRaw);

        UldDTO uldDto = new UldDTO();
        uldDto.setUldNumber(uld);
        uldDto.setFlightId(flightId);
        uldDto.setAirlineId(airlineId);
        uldDto.setGrossWeightLbs(parseDecimal(grossRaw));
        uldDto.setTareLbs(parseDecimal(tareRaw));
        uldDto.setUldType(type);
        uldDto.setConfig(config == null ? type : config);
        uldDto.setSealNumber(isBlank(sealRaw) ? "" : sealRaw.trim());
        uldDto.setPosition(isBlank(posRaw) ? "" : posRaw.trim());
        uldDto.setStatus("OPEN");
        // Ingesta masiva: el operador completará Cargado/Pesado/Confirmado después.
        uldDto.setSkipOperatorValidation(true);

        uldMap.put(uld, uldDto);
    }

    private Row findHeaderRow(Sheet sheet) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 30); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) continue;
                if (ULD_ALIASES.contains(normalize(getFormatterCellValue(cell)))) {
                    return row;
                }
            }
        }
        return null;
    }

    private int findHeaderIndex(List<List<String>> rows) {
        for (int i = 0; i < Math.min(rows.size(), 30); i++) {
            for (String cell : rows.get(i)) {
                if (ULD_ALIASES.contains(normalize(cell))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> cols = new LinkedHashMap<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            String kind = kindOf(normalize(getFormatterCellValue(headerRow.getCell(c))));
            if (kind != null && !cols.containsKey(kind)) {
                cols.put(kind, c);
            }
        }
        return cols;
    }

    private Map<String, Integer> mapColumns(List<String> headerRow) {
        Map<String, Integer> cols = new LinkedHashMap<>();
        for (int c = 0; c < headerRow.size(); c++) {
            String kind = kindOf(normalize(headerRow.get(c)));
            if (kind != null && !cols.containsKey(kind)) {
                cols.put(kind, c);
            }
        }
        return cols;
    }

    private String kindOf(String normalized) {
        if (ULD_ALIASES.contains(normalized)) return KIND_ULD;
        if (MAWB_ALIASES.contains(normalized)) return KIND_MAWB;
        if (GROSS_ALIASES.contains(normalized)) return KIND_GROSS;
        if (TARE_ALIASES.contains(normalized)) return KIND_TARE;
        if (CONFIG_ALIASES.contains(normalized)) return KIND_CONFIG;
        if (SEAL_ALIASES.contains(normalized)) return KIND_SEAL;
        if (POS_ALIASES.contains(normalized)) return KIND_POS;
        return null;
    }

    private int colOf(Map<String, Integer> cols, String kind) {
        Integer idx = cols.get(kind);
        return idx == null ? -1 : idx;
    }

    private String valueAt(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size()) return "";
        String v = row.get(idx);
        return v == null ? "" : v;
    }

    private List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out;
    }

    private BigDecimal parseDecimal(String raw) {
        if (isBlank(raw)) return BigDecimal.ZERO;
        String s = raw.trim().replace(",", "").replaceAll("[^0-9.\\-]", "");
        if (s.isEmpty() || "-".equals(s) || ".".equals(s) || "-.".equals(s)) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String cleanConfig(String raw) {
        if (isBlank(raw)) return null;
        String s = raw.trim().toUpperCase(Locale.ROOT);
        return s.matches("^[A-Z0-9]{3,5}$") ? s : null;
    }

    private String detectUldType(String uldNumber) {
        if (isBlank(uldNumber)) return "BULK";
        String prefix = uldNumber.split("-")[0].trim().toUpperCase(Locale.ROOT);
        return prefix.matches("^[A-Z0-9]{3,5}$") ? prefix : "BULK";
    }

    private String getCellValueResolved(Sheet sheet, Cell cell) {
        if (cell == null) return "";

        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.isInRange(cell.getRowIndex(), cell.getColumnIndex())) {
                Row masterRow = sheet.getRow(region.getFirstRow());
                Cell masterCell = masterRow.getCell(region.getFirstColumn());
                return getFormatterCellValue(masterCell);
            }
        }
        return getFormatterCellValue(cell);
    }

    private String getFormatterCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: {
                CellType cached = cell.getCachedFormulaResultType();
                if (cached == CellType.STRING) return cell.getStringCellValue();
                if (cached == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
                if (cached == CellType.NUMERIC) return String.valueOf(cell.getNumericCellValue());
                return "";
            }
            default: return "";
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD);
        t = t.replaceAll("\\p{M}", "");
        t = t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9%]", "");
        return t;
    }

    private static Set<String> normSet(String... values) {
        Set<String> set = new HashSet<>();
        for (String v : values) {
            set.add(normalize(v));
        }
        return set;
    }
}
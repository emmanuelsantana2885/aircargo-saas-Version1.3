package com.aircargo.exportservice.dto;

import java.util.List;
import java.util.Map;

/** Configuración completa de un reporte (lo que se guarda / evalúa). */
public record ReportConfigDTO(
    String name,
    String dimension,               // campo de agrupación (p.ej. "FlightNumber")
    List<String> fieldSources,      // columnas de datos a incluir por fila
    List<CalculatedFieldDTO> formulas,
    Map<String, Object> scenario,   // variables de proyección
    Map<String, Object> chartConfig, // {type, xAxis, series, topN, y}
    String baseSource               // tabla base de filas (mawb|booking|receipt|flight|uld|uld-awb)
) {
}

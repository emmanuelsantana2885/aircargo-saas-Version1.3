package com.aircargo.exportservice.dto;

import java.util.List;
import java.util.Map;

/** Resultado de la evaluación: filas + totales + campos disponibles. */
public record EvaluateResult(
    List<Map<String, Object>> rows,
    List<Map<String, Object>> totals,
    List<String> columns
) {
}

package com.aircargo.exportservice.dto;

import java.util.List;
import java.util.Map;

/**
 * Configuración de una tabla dinámica (pivot) sobre la tabla base elegida.
 * {@code rows} son los campos de dimensión que forman las filas; {@code values}
 * son las medidas con su agregación; {@code column} es una dimensión opcional
 * cuyos valores distintos se convierten en grupos de columnas. Los filtros viajan
 * en {@code chartConfig["filters"]} como en el modo plano.
 */
public record PivotConfig(
    String baseSource,
    List<String> rows,
    List<PivotValue> values,
    String column,
    Map<String, Object> chartConfig
) {
    public static PivotConfig empty() {
        return new PivotConfig("mawb", List.of(), List.of(), null, null);
    }
}
package com.aircargo.exportservice.dto;

import java.util.List;

/** Una fila de la tabla dinámica: etiquetas de la dimensión + celdas por {@code colGroup::measure}. */
public record PivotRow(
    List<String> key,
    List<Object> cells
) {
}
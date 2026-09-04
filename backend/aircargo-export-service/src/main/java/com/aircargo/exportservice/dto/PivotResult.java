package com.aircargo.exportservice.dto;

import java.util.List;
import java.util.Map;

/**
 * Resultado de una tabla dinámica. {@code colGroups} son los grupos de columnas
 * (p.ej. valores distintos de la dimensión {@code column}, o solo {@code ["__total"]}
 * si no hay columna pivot). {@code measures} son las etiquetas de las medidas
 * (p.ej. "Sum(MawbPieces)"). Cada {@link PivotRow} expone {@code key} (etiquetas de
 * fila) y {@code cells} (valores en el mismo orden que {@code colGroups} × {@code measures}).
 */
public record PivotResult(
    String baseSource,
    List<String> rowFields,
    List<String> colGroups,
    List<String> measures,
    List<PivotRow> rows,
    List<Object> totals
) {

    /** índice dentro de {@code cells} para un grupo de columna y una medida. */
    public static int cellIndex(int colIdx, int measureIdx, int measureCount) {
        return colIdx * measureCount + measureIdx;
    }
}
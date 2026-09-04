package com.aircargo.exportservice.dto;

/** Una medida de la tabla dinámica: campo + agregación (SUM/AVG/MAX/MIN/COUNT). */
public record PivotValue(
    String field,
    String agg
) {
    public PivotValue {
        if (agg == null || agg.isBlank()) agg = "SUM";
        agg = agg.toUpperCase();
    }
}
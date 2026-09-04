package com.aircargo.exportservice.dto;

/** Catálogo de un campo disponible para el reporte calculable. */
public record FieldDefDTO(
    String key,          // p.ej. "UldType"
    String label,        // "Tipo ULD"
    String unit,         // "lbs" | "kg" | "" (unidad de negocio)
    String type,         // "number" | "string" | "date"
    String source,       // "uld" | "mawb" | "flight" | "scenario"
    String hint          // texto de ayuda / fórmula sugerida
) {
}

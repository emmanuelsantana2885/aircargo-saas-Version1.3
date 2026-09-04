package com.aircargo.exportservice.dto;

/** Una columna calculada definida por el usuario. */
public record CalculatedFieldDTO(
    String column,           // nombre de la columna resultante
    String expression,       // fórmula, p.ej. "[NetLbs] - TareOf([UldType])"
    String aggregate         // "" por-fila | SUM | AVG | MAX | MIN | COUNT
) {
}

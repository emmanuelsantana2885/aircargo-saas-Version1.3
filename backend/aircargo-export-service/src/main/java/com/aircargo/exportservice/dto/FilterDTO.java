package com.aircargo.exportservice.dto;

/**
 * Filtro de consulta (WHERE) del Dashboard Builder.
 * op ∈ { eq, ne, contains, gt, gte, lt, lte, isNull, notNull }.
 */
public record FilterDTO(
    String field,
    String op,
    Object value
) {
}
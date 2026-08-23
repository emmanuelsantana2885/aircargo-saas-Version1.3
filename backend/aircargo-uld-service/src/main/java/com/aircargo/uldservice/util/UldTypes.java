package com.aircargo.uldservice.util;

import java.util.regex.Pattern;

/**
 * Normalización y validación de códigos de tipo ULD.
 * Los tipos estándar IATA son 3 letras (ej. PMC, AKE); se toleran hasta 5
 * caracteres alfanuméricos para convenciones internas como BULK.
 */
public final class UldTypes {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9]{3,5}$");

    private UldTypes() {}

    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toUpperCase();
    }

    public static boolean isValid(String code) {
        return code != null && CODE.matcher(code).matches();
    }
}

package com.aircargo.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converter JPA transparente: cifra al escribir, descifra al leer.
 * Usar con @Convert(converter = CryptoAttributeConverter.class) en campos sensibles.
 */
@Converter(autoApply = false)
public class CryptoAttributeConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return Crypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return Crypto.decrypt(dbData);
    }
}

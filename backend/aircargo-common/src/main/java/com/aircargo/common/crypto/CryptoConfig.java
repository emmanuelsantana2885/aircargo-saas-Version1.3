package com.aircargo.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Inicializa el cifrado en reposo desde la propiedad app.crypto.key
 * (Base64 de 32 bytes, ej: openssl rand -base64 32).
 * Si no está definida, los campos sensibles quedan sin cifrar (con warning).
 */
@Configuration
public class CryptoConfig {

    private static final Logger log = LoggerFactory.getLogger(CryptoConfig.class);

    @Value("${app.crypto.key:}")
    private String base64Key;

    @PostConstruct
    void init() {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("app.crypto.key NO definida — mfaSecret/cédulas/firmas se guardan SIN cifrado en reposo");
            return;
        }
        Crypto.init(base64Key);
    }
}

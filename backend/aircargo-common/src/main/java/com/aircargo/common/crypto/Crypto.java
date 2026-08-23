package com.aircargo.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
/**
 * Cifrado simétrico AES-256-GCM para datos sensibles en reposo
 * (secrets MFA, cédulas, firmas).
 *
 * Formato almacenado: "enc:v1:" + Base64(iv[12] || ciphertext+tag)
 * - Valores sin el prefijo se consideran legacy en texto plano y pasan tal cual
 *   al leerse (migración perezosa: se cifran la próxima vez que se escriban).
 * - Sin key configurada (app.crypto.key vacía) todo pasa sin cambios, con warning.
 */
public final class Crypto {

    private static final Logger log = LoggerFactory.getLogger(Crypto.class);
    private static final String PREFIX = "enc:v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private static volatile SecretKey key;
    private static boolean warned = false;

    private Crypto() {
    }

    /** @param base64Key clave de 32 bytes codificada en Base64; vacía/null desactiva el cifrado */
    public static synchronized void init(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            key = null;
            return;
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != 32) {
            throw new IllegalArgumentException("app.crypto.key debe decodificar exactamente 32 bytes (openssl rand -base64 32)");
        }
        key = new SecretKeySpec(raw, "AES");
        log.info("Cifrado en reposo ACTIVADO (AES-256-GCM)");
    }

    public static boolean enabled() {
        return key != null;
    }

    public static String encrypt(String plain) {
        SecretKey k = key;
        if (k == null || plain == null || plain.isEmpty() || plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Fallo al cifrar dato sensible", e);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored; // legacy en texto plano
        }
        SecretKey k = key;
        if (k == null) {
            if (!warned) {
                warned = true;
                log.error("Hay datos cifrados en BD pero app.crypto.key NO está definida — se devolverán ilegibles hasta configurarla");
            }
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            return new String(cipher.doFinal(all, IV_LEN, all.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Fallo al descifrar dato sensible (¿ cambió app.crypto.key ?)", e);
        }
    }
}

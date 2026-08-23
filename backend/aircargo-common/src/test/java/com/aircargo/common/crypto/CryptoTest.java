package com.aircargo.common.crypto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @BeforeEach
    void setUp() {
        Crypto.init(KEY);
    }

    @AfterEach
    void tearDown() {
        Crypto.init(null);
    }

    @Test
    void encrypt_producePrefijoYNoEsLegible() {
        String plain = "402-1234567-8";
        String enc = Crypto.encrypt(plain);
        assertTrue(enc.startsWith("enc:v1:"));
        assertFalse(enc.contains(plain));
        assertNotEquals(plain, enc);
    }

    @Test
    void roundtrip_recuperaTextoOriginal() {
        String secret = "JBSWY3DPEHPK3PXP";
        assertEquals(secret, Crypto.decrypt(Crypto.encrypt(secret)));
    }

    @Test
    void ivUnico_porCadaCifrado() {
        String a = Crypto.encrypt("mismo-texto");
        String b = Crypto.encrypt("mismo-texto");
        assertNotEquals(a, b, "GCM debe usar IV aleatorio por operación");
    }

    @Test
    void legacyPlaintext_pasaSinCambiosAlLeer() {
        assertEquals("dato-viejo", Crypto.decrypt("dato-viejo"));
        assertNull(Crypto.decrypt(null));
        assertEquals("", Crypto.decrypt(""));
    }

    @Test
    void sinKey_encryptEsPassthrough_yDecryptDeCifradoDevuelveIlegible() {
        Crypto.init(null);
        assertEquals("plano", Crypto.encrypt("plano"));
        String cifradaConKey = "enc:v1:AAAA";
        assertEquals(cifradaConKey, Crypto.decrypt(cifradaConKey)); // no explota
    }

    @Test
    void keyInvalida_rechazada() {
        assertThrows(IllegalArgumentException.class, () -> Crypto.init(Base64.getEncoder().encodeToString(new byte[16])));
    }
}

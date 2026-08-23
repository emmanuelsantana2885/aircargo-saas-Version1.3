package com.aircargo.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IpAnonymizerTest {

    @Test
    void ipv4_ultimoOctetoACero() {
        assertEquals("192.168.1.0", IpAnonymizer.truncate("192.168.1.45"));
        assertEquals("10.0.0.0", IpAnonymizer.truncate("10.0.0.1"));
    }

    @Test
    void ipv6_truncaUltimosGrupos() {
        String out = IpAnonymizer.truncate("2001:db8:85a3:1:2:3:4:5");
        assertTrue(out.startsWith("2001:db8:85a3:1:"), out);
        assertFalse(out.endsWith(":5"), out);
    }

    @Test
    void nullYVacioPassthrough() {
        assertNull(IpAnonymizer.truncate(null));
        assertEquals("", IpAnonymizer.truncate(""));
        assertEquals("  ", IpAnonymizer.truncate("  "));
    }

    @Test
    void formatoDesconocidoSeDejaIgual() {
        assertEquals("no-es-ip", IpAnonymizer.truncate("no-es-ip"));
    }
}

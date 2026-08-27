package com.aircargo.common.audit;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditServiceTest {

    private final UUID id = UUID.randomUUID();

    /** Fakes manuales: byte-buddy no puede mockear clases concretas en JDK 25. */
    static class FakeJdbc extends JdbcTemplate {
        int calls;
        Object[] args;

        @Override
        public int update(String sql, Object... args) {
            if (boom != null) throw boom;
            calls++;
            this.args = args;
            return 1;
        }

        Object arg(int i) { return args == null ? null : args[i]; }
        RuntimeException boom;
    }

    @Test
    void conBD_persisteUnSoloRegistro() {
        FakeJdbc jdbc = new FakeJdbc();

        AuditService svc = new AuditService(jdbc);
        assertDoesNotThrow(() ->
                svc.log(id, "a@x.com", "Ana", "USER_UPDATED", "USER", id.toString(), "{}", "10.1.2.3"));
        assertEquals(1, jdbc.calls);
    }

    @Test
    void insertFallido_noLanzaYAdvierte() {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.boom = new RuntimeException("bd caida");

        AuditService svc = new AuditService(jdbc);
        assertDoesNotThrow(() ->
                svc.log(id, "a@x.com", "Ana", "MAWB_UPDATED", "MAWB", id.toString(), null, "10.1.2.5"));
        assertEquals(0, jdbc.calls);  // el fallo se registra en el log de la app, no revienta la request
    }

    @Test
    void sinBD_noLanza() {
        AuditService svc = new AuditService(null);
        assertDoesNotThrow(() ->
                svc.log(id, "a@x.com", "Ana", "X", null, null, null, null));
    }

    @Test
    void ipSePseudonimizaEnInsert() {
        FakeJdbc jdbc = new FakeJdbc();

        AuditService svc = new AuditService(jdbc);
        svc.log(id, "a@x.com", "Ana", "X", null, null, null, "192.168.23.45");
        assertEquals("192.168.23.0", jdbc.arg(7));
    }

    @Test
    void detailsNulosSeSanitizan() {
        FakeJdbc jdbc = new FakeJdbc();

        AuditService svc = new AuditService(jdbc);
        svc.log(id, "a@x.com", "Ana", "X", null, null, null, "10.0.0.1");

        // details (índice 6) nunca debe llegar null al INSERT (TextUtil.safe)
        assertNotNull(jdbc.arg(6));
        assertNull(jdbc.arg(4)); // entityType sí puede ser null
    }
}

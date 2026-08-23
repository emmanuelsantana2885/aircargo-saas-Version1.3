package com.aircargo.common.audit;

import com.aircargo.common.event.AuditLogEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditServiceTest {

    private final UUID id = UUID.randomUUID();

    /** Fakes manuales: byte-buddy no puede mockear clases concretas en JDK 25. */
    static class FakeJdbc extends JdbcTemplate {
        int calls;
        Object lastArg;

        @Override
        public int update(String sql, Object... args) {
            calls++;
            lastArg = args.length > 0 ? args[args.length - 1] : null;
            return 1;
        }
    }

    static class FakeRabbit extends RabbitTemplate {
        int sends;
        RuntimeException boom;

        @Override
        public void convertAndSend(String exchange, String routingKey, Object message) {
            if (boom != null) throw boom;
            sends++;
            assert message instanceof AuditLogEvent;
        }
    }

    @Test
    void conBrokerYBD_escribeLocalYPublica() {
        FakeRabbit rt = new FakeRabbit();
        FakeJdbc jdbc = new FakeJdbc();

        AuditService svc = new AuditService(Optional.of(rt), jdbc);
        svc.log(id, "a@x.com", "Ana", "USER_UPDATED", "USER", id.toString(), "{}", "10.1.2.3");

        assertEquals(1, jdbc.calls);
        assertEquals(1, rt.sends);
    }

    @Test
    void sinBroker_conBD_fallbackLocalNoPierdeElEvento() {
        FakeJdbc jdbc = new FakeJdbc();

        AuditService svc = new AuditService(Optional.empty(), jdbc);
        assertDoesNotThrow(() ->
                svc.log(id, "a@x.com", "Ana", "BOOKING_CREATED", "BOOKING", id.toString(), null, "10.1.2.4"));
        assertEquals(1, jdbc.calls);
    }

    @Test
    void brokerCaido_conBD_registraPorFallbackSinLanzar() {
        FakeRabbit rt = new FakeRabbit();
        rt.boom = new RuntimeException("broker down");
        FakeJdbc jdbc = new FakeJdbc();

        AuditService svc = new AuditService(Optional.of(rt), jdbc);
        assertDoesNotThrow(() ->
                svc.log(id, "a@x.com", "Ana", "MAWB_UPDATED", "MAWB", id.toString(), null, "10.1.2.5"));
        assertEquals(1, jdbc.calls);  // el evento NO se pierde aunque el broker caiga
    }

    @Test
    void sinBrokerNiBD_noLanza() {
        AuditService svc = new AuditService(Optional.empty(), null);
        assertDoesNotThrow(() ->
                svc.log(id, "a@x.com", "Ana", "X", null, null, null, null));
    }

    @Test
    void ipSePseudonimizaEnInsert() {
        FakeJdbc jdbc = new FakeJdbc();

        AuditService svc = new AuditService(Optional.empty(), jdbc);
        svc.log(id, "a@x.com", "Ana", "X", null, null, null, "192.168.23.45");

        assertEquals("192.168.23.0", jdbc.lastArg);
    }
}

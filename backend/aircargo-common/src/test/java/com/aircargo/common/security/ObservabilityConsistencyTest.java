package com.aircargo.common.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GUARD DE CONSISTENCIA DE OBSERVABILIDAD + RESILIENCIA RABBITMQ.
 *
 * Escanea los application.properties de cada microservicio y falla el build si:
 *  O1. /actuator/prometheus NO está expuesto (Prometheus no podría scrapear).
 *  O2. Falta management.metrics.tags.application (etiqueta por servicio en Grafana).
 *  R1. Servicios que publican AMQP sin publisher-confirm-type=correlated
 *      (publicaciones silenciosamente perdidas ante caída del broker).
 *  R2. notification-service sin publisher-returns/mandatory (mensajes no enrutables invisibles).
 *
 * Patrón: mismo enfoque que SecurityConfigConsistencyTest — el drift de config
 * entre servicios no puede llegar a producción sin romper el build.
 */
class ObservabilityConsistencyTest {

    private static final Path BACKEND = resolverBackend();

    private static Path resolverBackend() {
        for (Path c : List.of(Path.of("..", "backend"), Path.of("backend"), Path.of("..", "..", "backend"))) {
            Path p = c.toAbsolutePath().normalize();
            if (Files.isDirectory(p.resolve("aircargo-common"))) return p;
        }
        throw new IllegalStateException("No se encontró el directorio backend/");
    }

    private static final List<String> ALL_SERVICES = List.of(
            "aircargo-gateway",
            "aircargo-auth-service", "aircargo-flight-service", "aircargo-booking-service",
            "aircargo-mawb-service", "aircargo-warehouse-service", "aircargo-uld-service",
            "aircargo-load-planning-service", "aircargo-export-service", "aircargo-notification-service");

    /** Servicios con spring-boot-starter-amqp que publican eventos. */
    private static final List<String> AMQP_PUBLISHERS = List.of(
            "aircargo-flight-service", "aircargo-booking-service", "aircargo-mawb-service",
            "aircargo-warehouse-service", "aircargo-notification-service");

    @Test
    void todosLosServiciosExponenPrometheusConEtiqueta() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String service : ALL_SERVICES) {
            Path props = BACKEND.resolve(service)
                    .resolve("src/main/resources/application.properties");
            assertTrue(Files.exists(props), "No existe " + props);
            String content = Files.readString(props);

            // O1: endpoint prometheus expuesto
            if (!content.matches("(?s).*management\\.endpoints\\.web\\.exposure\\.include=[^\\n]*prometheus.*")) {
                violations.add(service + ": /actuator/prometheus no está expuesto");
            }
            // O2: etiqueta application para filtrar en Grafana
            if (!content.contains("management.metrics.tags.application=")) {
                violations.add(service + ": falta management.metrics.tags.application");
            }
        }
        assertTrue(violations.isEmpty(),
                "Violaciones de observabilidad:\n  " + String.join("\n  ", violations));
    }

    @Test
    public void publicadoresAmqpTienenPublisherConfirms() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String service : AMQP_PUBLISHERS) {
            Path props = BACKEND.resolve(service)
                    .resolve("src/main/resources/application.properties");
            String content = Files.readString(props);

            if (!content.contains("spring.rabbitmq.publisher-confirm-type=correlated")) {
                violations.add(service + ": sin publisher-confirm-type=correlated");
            }
            if (!content.contains("spring.rabbitmq.publisher-returns=true")
                    || !content.contains("spring.rabbitmq.template.mandatory=true")) {
                violations.add(service + ": sin publisher-returns/mandatory (mensajes no enrutables invisibles)");
            }
        }
        assertTrue(violations.isEmpty(),
                "Violaciones de resiliencia AMQP:\n  " + String.join("\n  ", violations));
    }

    @Test
    public void notificationListenerUsaRetryFactoryConDlq() throws IOException {
        Path rabbitConfig = BACKEND.resolve("aircargo-notification-service")
                .resolve("src/main/java/com/aircargo/notificationservice/config/RabbitConfig.java");
        String content = Files.readString(rabbitConfig);

        assertTrue(content.contains("deadLetterExchange"), "La cola principal debe tener DLX");
        assertTrue(content.contains("RetryInterceptorBuilder"), "Debe existir retry del consumidor");
        assertTrue(content.contains("RejectAndDontRequeueRecoverer"),
                "Tras agotar reintentos el mensaje debe ir a la DLQ, no re-encolarse");

        Path listener = BACKEND.resolve("aircargo-notification-service")
                .resolve("src/main/java/com/aircargo/notificationservice/listener/NotificationEventListener.java");
        assertTrue(Files.readString(listener).contains("containerFactory = \"retryListenerFactory\""),
                "El listener debe usar la factory con retry");
    }
}

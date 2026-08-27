package com.aircargo.notificationservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología AMQP resiliente:
 * - Cola principal durable con dead-lettering (x-dead-letter-exchange).
 * - Retry del consumidor: 3 intentos con backoff exponencial 1s→2s→4s.
 * - Tras agotar intentos el mensaje va a la DLQ (nunca se re-encola infinito).
 *
 * NOTA OPERATIVA: si existe una cola previa SIN argumentos DLQ, el broker
 * rechaza la redeclaración (PRECONDITION_FAILED). Borrarla una sola vez:
 *   rabbitmqctl delete_queue aircargo.notifications
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "spring.rabbitmq.listener.simple.auto-startup", havingValue = "true", matchIfMissing = true)
public class RabbitConfig {

    static final String EXCHANGE = "aircargo.events";
    static final String QUEUE_NOTIFICATIONS = "aircargo.notifications";
    static final String DLX = "aircargo.dlx";
    static final String QUEUE_DLQ = "aircargo.notifications.dlq";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    /** Cola de trabajo: durable + dead-letter hacia aircargo.dlx. */
    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATIONS)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(QUEUE_DLQ)
                .build();
    }

    /** DLQ: retiene mensajes que fallaron tras los reintentos para inspección/reproceso. */
    @Bean
    public Queue notificationsDlq() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    @Bean
    public DirectExchange dlx() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Binding dlqBinding(Queue notificationsDlq, DirectExchange dlx) {
        return BindingBuilder.bind(notificationsDlq).to(dlx).with(QUEUE_DLQ);
    }

    @Bean
    public Binding binding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with("receipt.created");
    }

    @Bean
    public Binding bookingBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with("booking.awb.updated");
    }

    @Bean
    public Binding flightBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with("flight.departed");
    }

    @Bean
    public Binding mawbBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with("mawb.status.changed");
    }
    // NOTA: no hay binding para "audit.log" — la auditoría se persiste DIRECTAMENTE
    // en la tabla compartida audit_log (com.aircargo.common.audit.AuditService).
    // El binding+consumidor anterior generaba un registro duplicado por evento.

    /**
     * Factory con reintentos: 3 intentos (1s, 2s, 4s) y sin re-encolar —
     * los mensajes que siguen fallando terminan en la DLQ vía el recoverer.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory retryListenerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000L, 2.0, 10000L)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
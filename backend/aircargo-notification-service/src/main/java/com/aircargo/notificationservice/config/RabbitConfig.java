package com.aircargo.notificationservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "spring.rabbitmq.listener.simple.auto-startup", havingValue = "true", matchIfMissing = true)
public class RabbitConfig {

    static final String EXCHANGE = "aircargo.events";
    static final String QUEUE_NOTIFICATIONS = "aircargo.notifications";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue notificationsQueue() {
        return new Queue(QUEUE_NOTIFICATIONS, true);
    }

    @Bean
    public Binding binding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue)
                .to(eventsExchange)
                .with("receipt.created");
    }

    @Bean
    public Binding bookingBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue)
                .to(eventsExchange)
                .with("booking.awb.updated");
    }

    @Bean
    public Binding flightBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue)
                .to(eventsExchange)
                .with("flight.departed");
    }

    @Bean
    public Binding mawbBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue)
                .to(eventsExchange)
                .with("mawb.status.changed");
    }

    @Bean
    public Binding auditBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue)
                .to(eventsExchange)
                .with("audit.log");
    }
}

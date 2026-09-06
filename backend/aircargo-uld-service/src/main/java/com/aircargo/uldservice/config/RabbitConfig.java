package com.aircargo.uldservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitConfig {

    public static final String EXCHANGE = "aircargo.events";
    public static final String MAWB_SYNC_QUEUE = "aircargo.uld.mawb-sync";
    public static final String MAWB_UPDATED_KEY = "mawb.updated";

    @Bean
    public TopicExchange aircargoEventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue mawbSyncQueue() {
        return new Queue(MAWB_SYNC_QUEUE, true);
    }

    @Bean
    public Binding mawbSyncBinding(Queue mawbSyncQueue, TopicExchange aircargoEventsExchange) {
        return BindingBuilder.bind(mawbSyncQueue).to(aircargoEventsExchange).with(MAWB_UPDATED_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
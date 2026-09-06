package com.aircargo.loadplanningservice.config;

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
    public static final String INVALIDATE_QUEUE = "aircargo.loadplanning.invalidate";
    public static final String MAWB_UPDATED_KEY = "mawb.updated";
    public static final String ULD_UPDATED_KEY = "uld.updated";

    @Bean
    public TopicExchange aircargoEventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue loadPlanningInvalidateQueue() {
        return new Queue(INVALIDATE_QUEUE, true);
    }

    @Bean
    public Binding mawbInvalidateBinding(Queue loadPlanningInvalidateQueue, TopicExchange aircargoEventsExchange) {
        return BindingBuilder.bind(loadPlanningInvalidateQueue).to(aircargoEventsExchange).with(MAWB_UPDATED_KEY);
    }

    @Bean
    public Binding uldInvalidateBinding(Queue loadPlanningInvalidateQueue, TopicExchange aircargoEventsExchange) {
        return BindingBuilder.bind(loadPlanningInvalidateQueue).to(aircargoEventsExchange).with(ULD_UPDATED_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
package com.project.event_ticket_backend.util;

import com.project.event_ticket_backend.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventBus {

    private final RabbitTemplate rabbit;

    public void publish(String routingKey, Object payload) {
        rabbit.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, payload);
    }
}

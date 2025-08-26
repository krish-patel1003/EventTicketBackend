package com.project.event_ticket_backend.util;

import com.project.event_ticket_backend.booking.dto.PaymentCompletedDto;
import com.project.event_ticket_backend.booking.dto.PaymentRequestDto;
import com.project.event_ticket_backend.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentRequestConsumer {

    private final RabbitTemplate rabbit;

    @RabbitListener(queues = RabbitMQConfig.Q_PAYMENT_REQUESTED)
    public void onPaymentRequested(PaymentRequestDto request) throws InterruptedException{

        Thread.sleep(3000);
        boolean success = Math.random() < 0.85;
        var completed = new PaymentCompletedDto(
                request.bookingId(),
                success ? "SUCCESS": "FAILED",
                "TXN-" + UUID.randomUUID().toString().substring(0,8)
        );
        rabbit.convertAndSend(RabbitMQConfig.EXCHANGE,
        RabbitMQConfig.RK_PAYMENT_COMPLETED, completed);
    }

}

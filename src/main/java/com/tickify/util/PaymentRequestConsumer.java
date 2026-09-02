package com.tickify.util;

import com.tickify.booking.dto.PaymentCompletedDto;
import com.tickify.booking.dto.PaymentRequestDto;
import com.tickify.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in for a third-party payment provider.
 *
 * <p>Real card processing is slow and sometimes declines, and both facts shape the design:
 * that is why checkout is an asynchronous saga rather than a blocking HTTP call. This consumer
 * reproduces both characteristics so the rest of the system is exercised honestly — including
 * under load, where the artificial latency is what fills the queue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestConsumer {

    private final RabbitTemplate rabbit;

    /** Simulated provider round-trip. Set to 0 in load tests that target the booking path only. */
    @Value("${tickify.payment.simulated-latency-ms:3000}")
    private long simulatedLatencyMs;

    /** Simulated authorisation rate, so the failure branch of the saga is exercised too. */
    @Value("${tickify.payment.success-rate:0.85}")
    private double successRate;

    @RabbitListener(queues = RabbitMQConfig.Q_PAYMENT_REQUESTED)
    public void onPaymentRequested(PaymentRequestDto request) throws InterruptedException {

        if (simulatedLatencyMs > 0) {
            Thread.sleep(simulatedLatencyMs);
        }

        boolean success = ThreadLocalRandom.current().nextDouble() < successRate;

        var completed = new PaymentCompletedDto(
                request.bookingId(),
                success ? "SUCCESS" : "FAILED",
                "TXN-" + UUID.randomUUID().toString().substring(0, 8)
        );

        log.info("Payment {} for booking {} (amount {} {})",
                completed.status(), request.bookingId(), request.amount(), request.currency());

        rabbit.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_PAYMENT_COMPLETED, completed);
    }
}

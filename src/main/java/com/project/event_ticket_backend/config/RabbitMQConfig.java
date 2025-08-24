package com.project.event_ticket_backend.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "ticketing.exchange";
    public static final String Q_PAYMENT_REQUESTED  = "payment.requested.q";
    public static final String Q_PAYMENT_COMPLETED  = "payment.completed.q";
    public static final String Q_BOOKING_CONFIRMED  = "booking.confirmed.q";

    public static final String RK_PAYMENT_REQUESTED = "payment.requested";
    public static final String RK_PAYMENT_COMPLETED = "payment.completed";
    public static final String RK_BOOKING_CONFIRMED = "booking.confirmed";

    @Bean
    TopicExchange topicExchange() { return new TopicExchange(EXCHANGE, true, false); }

    @Bean
    Queue paymentRequestedQ() {
        return QueueBuilder.durable(Q_PAYMENT_REQUESTED).build();
    }

    @Bean
    Queue paymentCompletedQ() {
        return QueueBuilder.durable(Q_PAYMENT_COMPLETED).build();
    }

    @Bean
    Queue bookingConfirmed() {
        return QueueBuilder.durable(Q_BOOKING_CONFIRMED).build();
    }

    @Bean
    Binding bindPayReq(TopicExchange ex) {
        return BindingBuilder.bind(paymentRequestedQ()).to(ex).with(RK_PAYMENT_REQUESTED);
    }

    @Bean
    Binding bindPayCpl(TopicExchange ex) {
        return BindingBuilder.bind(paymentCompletedQ()).to(ex).with(RK_PAYMENT_COMPLETED);
    }

    @Bean
    Binding bindBookCfm(TopicExchange ex) {
        return BindingBuilder.bind(bookingConfirmed()).to(ex).with(RK_BOOKING_CONFIRMED);
    }


}

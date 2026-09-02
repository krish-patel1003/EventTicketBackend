package com.tickify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology and wire format for the booking saga.
 *
 * <pre>
 *   ticketing.exchange (topic)
 *     payment.requested  -> payment.requested.q   consumed by the payment service
 *     payment.completed  -> payment.completed.q   consumed by the booking service
 *     booking.confirmed  -> booking.confirmed.q   consumed by notifications
 * </pre>
 *
 * Every queue is durable: a broker restart mid-drop must not lose an in-flight payment and
 * leave a booking stuck in PENDING with its seats locked.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "ticketing.exchange";
    public static final String Q_PAYMENT_REQUESTED  = "payment.requested.q";
    public static final String Q_PAYMENT_COMPLETED  = "payment.completed.q";
    public static final String Q_BOOKING_CONFIRMED  = "booking.confirmed.q";

    public static final String RK_PAYMENT_REQUESTED = "payment.requested";
    public static final String RK_PAYMENT_COMPLETED = "payment.completed";
    public static final String RK_BOOKING_CONFIRMED = "booking.confirmed";

    /**
     * JSON on the wire.
     *
     * <p>Spring AMQP's default {@code SimpleMessageConverter} handles only String, byte[] and
     * {@link java.io.Serializable}. The saga's messages are Java records, so without this bean
     * every publish is rejected with "SimpleMessageConverter only supports String, byte[] and
     * Serializable payloads" and no payment is ever processed. JSON also keeps the messages
     * readable in the RabbitMQ console and decoupled from the producer's class names.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    /** The same converter on the consuming side, so listeners receive typed records. */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter converter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(converter);
        // A message the listener cannot handle is dropped rather than requeued. Requeuing is
        // the default, and it turns one poison message into an unbounded redelivery loop that
        // saturates the consumers and starves every healthy message behind it.
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

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

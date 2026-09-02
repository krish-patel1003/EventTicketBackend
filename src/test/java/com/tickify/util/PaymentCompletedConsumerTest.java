package com.tickify.util;

import com.tickify.booking.dto.BookingConfirmedDto;
import com.tickify.booking.dto.PaymentCompletedDto;
import com.tickify.booking.entity.Booking;
import com.tickify.booking.entity.BookingSeat;
import com.tickify.booking.entity.PaymentStatus;
import com.tickify.booking.entity.QRCode;
import com.tickify.booking.repository.BookingRepository;
import com.tickify.booking.repository.QRCodeRepository;
import com.tickify.booking.service.SeatLockService;
import com.tickify.config.RabbitMQConfig;
import com.tickify.event.entity.Event;
import com.tickify.event.entity.EventSeat;
import com.tickify.event.repository.EventSeatRepository;
import com.tickify.observability.BookingMetrics;
import com.tickify.user.entity.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The confirming half of the booking saga. These cases cover the transitions that decide
 * whether a seat can be sold twice or lost entirely.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCompletedConsumer")
class PaymentCompletedConsumerTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private EventSeatRepository eventSeatRepository;
    @Mock private SeatLockService seatLockService;
    @Mock private QRCodeRepository qrCodeRepository;
    @Mock private EventBus eventBus;

    private PaymentCompletedConsumer consumer;
    private SimpleMeterRegistry registry;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID seatId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        consumer = new PaymentCompletedConsumer(bookingRepository, eventSeatRepository,
                seatLockService, qrCodeRepository, eventBus, new BookingMetrics(registry));
    }

    private Booking pendingBooking() {
        User user = new User();
        user.setId(UUID.randomUUID());

        Event event = new Event();
        event.setId(eventId);

        EventSeat seat = new EventSeat();
        seat.setId(seatId);
        seat.setSeatNumber("A-R1-7");

        BookingSeat bookingSeat = new BookingSeat();
        bookingSeat.setSeat(seat);

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setUser(user);
        booking.setEvent(event);
        booking.setBookingReference("TICK-ABCD1234");
        booking.setBillingAmount(new BigDecimal("25.00"));
        booking.setPaymentStatus(PaymentStatus.PENDING);
        booking.setSeats(new ArrayList<>(List.of(bookingSeat)));

        bookingSeat.setBooking(booking);
        return booking;
    }

    @Test
    @DisplayName("a confirmed booking keeps its seat claim, so the seat cannot be resold")
    void successKeepsTheSeatClaim() throws Exception {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        consumer.onPaymentCompleted(new PaymentCompletedDto(bookingId, "SUCCESS", "TXN-1"));

        assertThat(booking.getSeats())
                .allSatisfy(bookingSeat -> assertThat(bookingSeat.getReleasedAt()).isNull());
    }

    @Test
    @DisplayName("a successful payment reserves the seats, mints a QR code and announces the booking")
    void successConfirmsBooking() throws Exception {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        consumer.onPaymentCompleted(new PaymentCompletedDto(bookingId, "SUCCESS", "TXN-1"));

        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);

        ArgumentCaptor<EventSeat> seat = ArgumentCaptor.forClass(EventSeat.class);
        verify(eventSeatRepository).save(seat.capture());
        assertThat(seat.getValue().isReserved())
                .as("the durable reservation is what survives a Redis restart")
                .isTrue();

        // The Redis lock is only dropped after the reservation is written, so the seat is
        // never simultaneously unlocked and unreserved.
        verify(seatLockService).releaseSeat(eventId, seatId);
        verify(qrCodeRepository).save(any(QRCode.class));

        ArgumentCaptor<BookingConfirmedDto> published = ArgumentCaptor.forClass(BookingConfirmedDto.class);
        verify(eventBus).publish(eq(RabbitMQConfig.RK_BOOKING_CONFIRMED), published.capture());
        assertThat(published.getValue().seatNumbers()).containsExactly("A-R1-7");
        assertThat(published.getValue().qrcode()).isNotBlank();

        assertThat(registry.get("tickify.booking").tag("state", "confirmed").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a declined payment puts the seats straight back on sale")
    void failureReleasesSeats() throws Exception {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        consumer.onPaymentCompleted(new PaymentCompletedDto(bookingId, "FAILED", "TXN-2"));

        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(seatLockService).releaseSeat(eventId, seatId);

        // The database claim has to be given up alongside the Redis lock. Releasing only
        // the lock puts the seat back on sale while booking_seats still claims it, and the
        // next buyer's insert then collides with the live-claim unique index.
        assertThat(booking.getSeats())
                .allSatisfy(bookingSeat -> assertThat(bookingSeat.getReleasedAt()).isNotNull());

        // Nothing was sold, so nothing is reserved and no ticket is issued.
        verify(eventSeatRepository, never()).save(any());
        verify(qrCodeRepository, never()).save(any());
        verify(eventBus, never()).publish(any(), any());

        assertThat(registry.get("tickify.booking").tag("state", "failed").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a redelivered payment.completed does not mint a second ticket")
    void redeliveryIsIgnored() throws Exception {
        Booking alreadyConfirmed = pendingBooking();
        alreadyConfirmed.setPaymentStatus(PaymentStatus.SUCCESS);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(alreadyConfirmed));

        // RabbitMQ guarantees at-least-once delivery, so this message can and will arrive twice.
        consumer.onPaymentCompleted(new PaymentCompletedDto(bookingId, "SUCCESS", "TXN-1"));

        verify(qrCodeRepository, never()).save(any());
        verify(eventBus, never()).publish(any(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("a late failure cannot cancel a booking that already succeeded")
    void lateFailureCannotUndoConfirmedBooking() throws Exception {
        Booking alreadyConfirmed = pendingBooking();
        alreadyConfirmed.setPaymentStatus(PaymentStatus.SUCCESS);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(alreadyConfirmed));

        consumer.onPaymentCompleted(new PaymentCompletedDto(bookingId, "FAILED", "TXN-3"));

        assertThat(alreadyConfirmed.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(seatLockService, never()).releaseSeat(any(), any());
    }
}

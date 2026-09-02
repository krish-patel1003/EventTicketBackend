package com.tickify.booking.utils;

import com.tickify.booking.entity.Booking;
import com.tickify.booking.entity.BookingSeat;
import com.tickify.booking.entity.PaymentStatus;
import com.tickify.booking.repository.BookingRepository;
import com.tickify.booking.service.SeatLockService;
import com.tickify.config.properties.TickifyProperties;
import com.tickify.event.entity.Event;
import com.tickify.event.entity.EventSeat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AbandonedBookingReaper")
class AbandonedBookingReaperTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private SeatLockService seatLockService;

    private AbandonedBookingReaper reaper;
    private TickifyProperties properties;

    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new TickifyProperties();
        properties.getSeatLock().setTtl(Duration.ofMinutes(10));
        properties.getBooking().setReaperGracePeriod(Duration.ofMinutes(2));

        reaper = new AbandonedBookingReaper(bookingRepository, seatLockService, properties);
    }

    private Booking abandonedBooking(UUID... seatIds) {
        Event event = new Event();
        event.setId(eventId);

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setBookingReference("TICK-ABANDON");
        booking.setEvent(event);
        booking.setPaymentStatus(PaymentStatus.PENDING);
        booking.setSeats(new ArrayList<>());

        for (UUID seatId : seatIds) {
            EventSeat seat = new EventSeat();
            seat.setId(seatId);

            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBooking(booking);
            bookingSeat.setSeat(seat);
            booking.getSeats().add(bookingSeat);
        }
        return booking;
    }

    @Test
    @DisplayName("releases the seat claim as well as the Redis lock")
    void releasesClaimAndLock() {
        UUID seatId = UUID.randomUUID();
        Booking booking = abandonedBooking(seatId);
        when(bookingRepository.findStalePendingBookings(eq(PaymentStatus.PENDING), any(), any(Limit.class)))
                .thenReturn(List.of(booking));

        reaper.releaseAbandonedBookings();

        // Dropping only the Redis lock is what left the seat unsellable: the booking_seats
        // row kept its live claim and the next buyer collided with the unique index.
        assertThat(booking.getSeats().get(0).getReleasedAt()).isNotNull();
        verify(seatLockService).releaseSeat(eventId, seatId);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(bookingRepository).saveAll(List.of(booking));
    }

    @Test
    @DisplayName("only looks back beyond the lock TTL plus the grace period")
    void cutoffAllowsForSlowPayments() {
        when(bookingRepository.findStalePendingBookings(any(), any(), any(Limit.class))).thenReturn(List.of());

        Instant before = Instant.now();
        reaper.releaseAbandonedBookings();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(bookingRepository).findStalePendingBookings(eq(PaymentStatus.PENDING), cutoff.capture(), any());

        // 10m TTL + 2m grace: a booking younger than 12 minutes must never be reaped, or a
        // payment still in flight would have its seats sold to somebody else.
        assertThat(cutoff.getValue()).isBefore(before.minus(Duration.ofMinutes(11)));
        assertThat(cutoff.getValue()).isAfter(before.minus(Duration.ofMinutes(13)));
    }

    @Test
    @DisplayName("does nothing when there is nothing to reap")
    void noopWhenNothingStale() {
        when(bookingRepository.findStalePendingBookings(any(), any(), any(Limit.class))).thenReturn(List.of());

        reaper.releaseAbandonedBookings();

        verify(bookingRepository, never()).saveAll(any());
        verify(seatLockService, never()).releaseSeat(any(), any());
    }

    @Test
    @DisplayName("skips a seat whose claim was already released")
    void skipsAlreadyReleasedSeats() {
        UUID live = UUID.randomUUID();
        UUID alreadyReleased = UUID.randomUUID();
        Booking booking = abandonedBooking(live, alreadyReleased);
        booking.getSeats().get(1).release(Instant.now().minusSeconds(60));

        when(bookingRepository.findStalePendingBookings(any(), any(), any(Limit.class)))
                .thenReturn(List.of(booking));

        reaper.releaseAbandonedBookings();

        verify(seatLockService).releaseSeat(eventId, live);
        verify(seatLockService, never()).releaseSeat(eventId, alreadyReleased);
    }
}

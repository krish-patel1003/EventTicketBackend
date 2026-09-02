package com.tickify.booking.service;

import com.tickify.booking.dto.LockSeatRequestDto;
import com.tickify.booking.entity.Booking;
import com.tickify.booking.entity.BookingSeat;
import com.tickify.booking.entity.PaymentStatus;
import com.tickify.booking.repository.BookingRepository;
import com.tickify.booking.repository.BookingSeatRepository;
import com.tickify.event.entity.Event;
import com.tickify.event.entity.EventSeat;
import com.tickify.event.entity.TicketType;
import com.tickify.event.repository.EventRepository;
import com.tickify.event.repository.EventSeatRepository;
import com.tickify.event.repository.TicketTypeRepository;
import com.tickify.observability.BookingMetrics;
import com.tickify.user.entity.User;
import com.tickify.user.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PendingBookingWriter")
class PendingBookingWriterTest {

    @Mock private BookingRepository bookingRepo;
    @Mock private BookingSeatRepository bookingSeatRepo;
    @Mock private EventSeatRepository eventSeatRepo;
    @Mock private EventRepository eventRepo;
    @Mock private TicketTypeRepository ticketTypeRepo;
    @Mock private UserRepository userRepo;
    @Mock private SeatLockService seatLockService;

    private PendingBookingWriter writer;

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID ticketTypeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        writer = new PendingBookingWriter(bookingRepo, bookingSeatRepo, eventSeatRepo, eventRepo,
                ticketTypeRepo, userRepo, seatLockService, new BookingMetrics(new SimpleMeterRegistry()));
    }

    private LockSeatRequestDto request(UUID... seats) {
        return new LockSeatRequestDto(eventId, List.of(seats), ticketTypeId);
    }

    private void givenLocksHeldAndSeatsUnsold(String price) {
        lenient().when(seatLockService.isLockedBy(eq(eventId), any(), eq(userId))).thenReturn(true);
        lenient().when(eventSeatRepo.countByIdInAndIsReservedTrue(anyCollection())).thenReturn(0L);
        lenient().when(userRepo.getReferenceById(userId)).thenReturn(new User());
        lenient().when(eventRepo.getReferenceById(eventId)).thenReturn(new Event());
        lenient().when(eventSeatRepo.getReferenceById(any())).thenReturn(new EventSeat());

        TicketType type = new TicketType();
        type.setPrice(new BigDecimal(price));
        lenient().when(ticketTypeRepo.getReferenceById(ticketTypeId)).thenReturn(type);

        lenient().when(bookingRepo.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("persists the booking before attaching its seats")
    void persistsBookingBeforeSeats() {
        UUID seatId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        givenLocksHeldAndSeatsUnsold("25.00");
        when(bookingRepo.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(bookingId);
            return b;
        });

        assertThat(writer.createPendingBooking(request(seatId), userId)).isEqualTo(bookingId);

        // BookingSeat has a non-optional reference to Booking; saving it against an
        // unpersisted booking fails with a transient-instance error.
        ArgumentCaptor<BookingSeat> seat = ArgumentCaptor.forClass(BookingSeat.class);
        verify(bookingSeatRepo).save(seat.capture());
        assertThat(seat.getValue().getBooking().getId()).isEqualTo(bookingId);
    }

    @Test
    @DisplayName("bills price x seat count, at 2dp, without floating point drift")
    void billsExactMoneyAmount() {
        givenLocksHeldAndSeatsUnsold("10.10");

        writer.createPendingBooking(request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), userId);

        ArgumentCaptor<Booking> booking = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepo).save(booking.capture());
        // 10.10 * 3 is 30.299999... in binary floating point.
        assertThat(booking.getValue().getBillingAmount()).isEqualByComparingTo(new BigDecimal("30.30"));
        assertThat(booking.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("refuses when a lock expired between selection and checkout")
    void refusesWhenLockHasExpired() {
        UUID seatId = UUID.randomUUID();
        when(seatLockService.isLockedBy(eventId, seatId, userId)).thenReturn(false);

        assertThatThrownBy(() -> writer.createPendingBooking(request(seatId), userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Lock missing");

        verify(bookingRepo, never()).save(any());
    }

    @Test
    @DisplayName("refuses a seat that has already been sold, even with no lock on it")
    void refusesAlreadySoldSeat() {
        UUID seatId = UUID.randomUUID();
        when(seatLockService.isLockedBy(eventId, seatId, userId)).thenReturn(true);
        // Confirming a booking releases its Redis lock, so a stale seat map lets a client
        // re-lock a sold seat. Only the reservation flag still says it is gone.
        when(eventSeatRepo.countByIdInAndIsReservedTrue(anyCollection())).thenReturn(1L);

        assertThatThrownBy(() -> writer.createPendingBooking(request(seatId), userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been sold");

        verify(bookingRepo, never()).save(any());
    }

    @Test
    @DisplayName("reports a live-claim collision as contention, not as a server fault")
    void constraintViolationBecomesConflict() {
        givenLocksHeldAndSeatsUnsold("25.00");
        doThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_booking_seats_live_claim\""))
                .when(bookingSeatRepo).flush();

        // The database is the final arbiter of "one live claim per seat". Losing that race
        // must surface as 409 to the caller, not as an unhandled 500.
        assertThatThrownBy(() -> writer.createPendingBooking(request(UUID.randomUUID()), userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("just been taken");
    }

    @Test
    @DisplayName("issues a booking reference that fits the column")
    void bookingReferenceFitsColumn() {
        givenLocksHeldAndSeatsUnsold("25.00");

        writer.createPendingBooking(request(UUID.randomUUID()), userId);

        ArgumentCaptor<Booking> booking = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepo).save(booking.capture());
        assertThat(booking.getValue().getBookingReference())
                .startsWith("TICK-")
                .hasSizeLessThanOrEqualTo(20);
    }
}

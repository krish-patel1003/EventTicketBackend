package com.tickify.booking.service;

import com.tickify.booking.dto.LockSeatRequestDto;
import com.tickify.booking.dto.PaymentRequestDto;
import com.tickify.booking.entity.Booking;
import com.tickify.booking.entity.PaymentStatus;
import com.tickify.booking.mapper.BookingMapper;
import com.tickify.booking.repository.BookingRepository;
import com.tickify.booking.repository.QRCodeRepository;
import com.tickify.config.RabbitMQConfig;
import com.tickify.user.entity.User;
import com.tickify.user.repository.UserRepository;
import com.tickify.util.EventBus;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService")
class BookingServiceTest {

    @Mock private QueueService queueService;
    @Mock private SeatLockService seatLockService;
    @Mock private PendingBookingWriter pendingBookingWriter;
    @Mock private BookingRepository bookingRepo;
    @Mock private UserRepository userRepo;
    @Mock private EventBus eventBus;
    @Mock private BookingMapper bookingMapper;
    @Mock private QRCodeRepository qrCodeRepo;

    private BookingService bookingService;

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID ticketTypeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(queueService, seatLockService, pendingBookingWriter,
                bookingRepo, userRepo, eventBus, bookingMapper, qrCodeRepo);
    }

    private LockSeatRequestDto request(UUID... seats) {
        return new LockSeatRequestDto(eventId, List.of(seats), ticketTypeId);
    }

    @Nested
    @DisplayName("waiting-room gate")
    class WaitingRoomGate {

        @Test
        @DisplayName("rejects a user with no active slot")
        void rejectsUserWithoutSlot() {
            when(queueService.hasActiveSlot(eventId, userId)).thenReturn(false);

            assertThatThrownBy(() -> bookingService.ensureActiveSlot(eventId, userId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("active booking slot");
        }

        @Test
        @DisplayName("admits a user holding an active slot")
        void admitsUserWithSlot() {
            when(queueService.hasActiveSlot(eventId, userId)).thenReturn(true);

            assertThatCode(() -> bookingService.ensureActiveSlot(eventId, userId)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("seat locking")
    class SeatLocking {

        @Test
        @DisplayName("locks every requested seat when all are free")
        void locksAllSeats() {
            UUID seatA = UUID.randomUUID();
            UUID seatB = UUID.randomUUID();
            when(seatLockService.tryLockSeat(eq(eventId), any(), eq(userId))).thenReturn(true);

            bookingService.lockSeats(request(seatA, seatB), userId);

            verify(seatLockService).tryLockSeat(eventId, seatA, userId);
            verify(seatLockService).tryLockSeat(eventId, seatB, userId);
        }

        @Test
        @DisplayName("is all-or-nothing: a contended seat aborts the whole selection")
        void abortsOnContendedSeat() {
            UUID seatA = UUID.randomUUID();
            UUID seatB = UUID.randomUUID();
            when(seatLockService.tryLockSeat(eventId, seatA, userId)).thenReturn(true);
            when(seatLockService.tryLockSeat(eventId, seatB, userId)).thenReturn(false);

            assertThatThrownBy(() -> bookingService.lockSeats(request(seatA, seatB), userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(seatB.toString());
        }

        @Test
        @DisplayName("rolls back only the locks it took, and only as their owner")
        void rollbackReleasesOnlyOwnAcquiredLocks() {
            UUID mine = UUID.randomUUID();
            UUID theirs = UUID.randomUUID();
            UUID untouched = UUID.randomUUID();

            when(seatLockService.tryLockSeat(eventId, mine, userId)).thenReturn(true);
            when(seatLockService.tryLockSeat(eventId, theirs, userId)).thenReturn(false);

            assertThatThrownBy(() -> bookingService.lockSeats(request(mine, theirs, untouched), userId))
                    .isInstanceOf(IllegalStateException.class);

            // Only the seat this call actually locked is handed back...
            verify(seatLockService).releaseSeat(eventId, mine, userId);
            // ...never the seat held by the other user, and never one we never reached.
            verify(seatLockService, never()).releaseSeat(eventId, theirs, userId);
            verify(seatLockService, never()).releaseSeat(eventId, untouched, userId);
        }
    }

    @Nested
    @DisplayName("lock and create")
    class LockAndCreate {

        @Test
        @DisplayName("hands the locks back when the booking cannot be written")
        void releasesLocksWhenTheWriteFails() {
            UUID seatA = UUID.randomUUID();
            UUID seatB = UUID.randomUUID();
            when(seatLockService.tryLockSeat(eq(eventId), any(), eq(userId))).thenReturn(true);
            when(pendingBookingWriter.createPendingBooking(any(), eq(userId)))
                    .thenThrow(new IllegalStateException("One of those seats has just been taken"));

            assertThatThrownBy(() -> bookingService.lockSeatsAndCreateBooking(request(seatA, seatB), userId))
                    .isInstanceOf(IllegalStateException.class);

            // Left behind, these locks would hold both seats for the full TTL on behalf of
            // a booking that was never created.
            verify(seatLockService).releaseSeat(eventId, seatA, userId);
            verify(seatLockService).releaseSeat(eventId, seatB, userId);
        }

        @Test
        @DisplayName("returns the new booking id on the happy path")
        void returnsBookingId() {
            UUID seatId = UUID.randomUUID();
            UUID bookingId = UUID.randomUUID();
            when(seatLockService.tryLockSeat(eq(eventId), any(), eq(userId))).thenReturn(true);
            when(pendingBookingWriter.createPendingBooking(any(), eq(userId))).thenReturn(bookingId);

            assertThat(bookingService.lockSeatsAndCreateBooking(request(seatId), userId))
                    .isEqualTo(bookingId);

            verify(seatLockService, never()).releaseSeat(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("payment initiation")
    class PaymentInitiation {

        @Test
        @DisplayName("publishes payment.requested for the owner's booking")
        void publishesPaymentRequested() {
            UUID bookingId = UUID.randomUUID();
            when(bookingRepo.findById(bookingId)).thenReturn(Optional.of(booking(bookingId, userId, "50.00")));

            bookingService.initiatePayment(bookingId, userId);

            ArgumentCaptor<PaymentRequestDto> msg = ArgumentCaptor.forClass(PaymentRequestDto.class);
            verify(eventBus).publish(eq(RabbitMQConfig.RK_PAYMENT_REQUESTED), msg.capture());
            assertThat(msg.getValue().bookingId()).isEqualTo(bookingId);
            assertThat(msg.getValue().amount()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(msg.getValue().currency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("refuses to pay for someone else's booking")
        void refusesForeignBooking() {
            UUID bookingId = UUID.randomUUID();
            when(bookingRepo.findById(bookingId))
                    .thenReturn(Optional.of(booking(bookingId, UUID.randomUUID(), "50.00")));

            assertThatThrownBy(() -> bookingService.initiatePayment(bookingId, userId))
                    .isInstanceOf(AccessDeniedException.class);

            verify(eventBus, never()).publish(any(), any());
        }

        @Test
        @DisplayName("404s on an unknown booking")
        void unknownBooking() {
            UUID bookingId = UUID.randomUUID();
            when(bookingRepo.findById(bookingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.initiatePayment(bookingId, userId))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("booking lookup")
    class BookingLookup {

        @Test
        @DisplayName("refuses to return another user's booking")
        void refusesForeignBooking() {
            UUID bookingId = UUID.randomUUID();
            when(bookingRepo.findById(bookingId))
                    .thenReturn(Optional.of(booking(bookingId, UUID.randomUUID(), "50.00")));

            assertThatThrownBy(() -> bookingService.getBooking(bookingId, userId))
                    .isInstanceOf(AccessDeniedException.class);

            verify(bookingMapper, never()).entityToDto(any(), any());
        }

        @Test
        @DisplayName("maps the caller's own booking")
        void mapsOwnBooking() {
            UUID bookingId = UUID.randomUUID();
            Booking own = booking(bookingId, userId, "50.00");
            when(bookingRepo.findById(bookingId)).thenReturn(Optional.of(own));
            when(qrCodeRepo.findByBooking_Id(bookingId)).thenReturn(Optional.empty());

            bookingService.getBooking(bookingId, userId);

            verify(bookingMapper, times(1)).entityToDto(own, null);
        }
    }

    private Booking booking(UUID bookingId, UUID ownerId, String amount) {
        User owner = new User();
        owner.setId(ownerId);

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setUser(owner);
        booking.setBillingAmount(new BigDecimal(amount));
        booking.setPaymentStatus(PaymentStatus.PENDING);
        return booking;
    }
}

package com.tickify.booking.service;

import com.tickify.booking.dto.LockSeatRequestDto;
import com.tickify.booking.entity.Booking;
import com.tickify.booking.entity.BookingSeat;
import com.tickify.booking.entity.PaymentStatus;
import com.tickify.booking.repository.BookingRepository;
import com.tickify.booking.repository.BookingSeatRepository;
import com.tickify.event.repository.EventRepository;
import com.tickify.event.repository.EventSeatRepository;
import com.tickify.event.repository.TicketTypeRepository;
import com.tickify.observability.BookingMetrics;
import com.tickify.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * The database half of opening a booking, kept in its own bean on purpose.
 *
 * <p>{@link BookingService} takes the Redis seat locks and then calls this. Both steps could
 * live in one class, but not in one method: a {@code @Transactional} method invoked from
 * inside the same bean goes straight to {@code this}, bypassing the Spring proxy, so the
 * transaction never starts and the first lazy association blows up with
 * "Could not initialize proxy - no session". Separating them also keeps the database
 * transaction from being held open across the Redis round-trips, which is the last thing
 * this path wants when a drop is in progress.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PendingBookingWriter {

    private final BookingRepository bookingRepo;
    private final BookingSeatRepository bookingSeatRepo;
    private final EventSeatRepository eventSeatRepo;
    private final EventRepository eventRepo;
    private final TicketTypeRepository ticketTypeRepo;
    private final UserRepository userRepo;
    private final SeatLockService seatLockService;
    private final BookingMetrics metrics;

    @Transactional
    public UUID createPendingBooking(LockSeatRequestDto request, UUID userId) {

        for (UUID seatId : request.seatIds()) {
            // Re-check ownership: the lock could have expired between selection and checkout.
            if (!seatLockService.isLockedBy(request.eventId(), seatId, userId)) {
                throw new IllegalStateException("Lock missing for seat: " + seatId);
            }
        }

        // A seat that has already been sold is off the market even though nothing holds a
        // Redis lock on it any more — confirming a booking releases the lock. Checking here
        // turns a stale seat map into a clean 409 rather than a constraint violation.
        if (eventSeatRepo.countByIdInAndIsReservedTrue(request.seatIds()) > 0) {
            throw new IllegalStateException("One of those seats has already been sold");
        }

        var user = userRepo.getReferenceById(userId);
        var event = eventRepo.getReferenceById(request.eventId());
        var ticketType = ticketTypeRepo.getReferenceById(request.ticketTypeId());

        var booking = new Booking();
        booking.setUser(user);
        booking.setEvent(event);
        booking.setTicketType(ticketType);
        booking.setBookingReference(generateRef());
        booking.setPaymentStatus(PaymentStatus.PENDING);
        booking.setBillingAmount(ticketType.getPrice()
                .multiply(BigDecimal.valueOf(request.seatIds().size()))
                .setScale(2, RoundingMode.HALF_UP));

        // The booking is saved before its seats: BookingSeat holds a non-optional reference
        // to it, so attaching seats to an unsaved booking fails as a transient instance.
        bookingRepo.save(booking);

        try {
            for (UUID seatId : request.seatIds()) {
                var bookingSeat = new BookingSeat();
                bookingSeat.setBooking(booking);
                bookingSeat.setSeat(eventSeatRepo.getReferenceById(seatId));
                bookingSeatRepo.save(bookingSeat);
            }
            bookingSeatRepo.flush();
        } catch (DataIntegrityViolationException e) {
            // uk_booking_seats_live_claim is the final arbiter: whatever the checks above
            // concluded, the database will not allow two live claims on one seat. Losing
            // that race is contention, not a server fault, so it is reported as one.
            log.info("Live-claim conflict for user {} on event {}: {}",
                    userId, request.eventId(), e.getMostSpecificCause().getMessage());
            throw new IllegalStateException("One of those seats has just been taken");
        }

        metrics.bookingCreated();
        log.info("Created pending booking {} ({}) for user {} on event {} with {} seat(s)",
                booking.getId(), booking.getBookingReference(), userId,
                request.eventId(), request.seatIds().size());

        return booking.getId();
    }

    private String generateRef() {
        return "TICK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

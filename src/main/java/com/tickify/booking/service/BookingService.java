package com.tickify.booking.service;

import com.tickify.booking.dto.BookingDto;
import com.tickify.booking.dto.LockSeatRequestDto;
import com.tickify.booking.dto.PaymentRequestDto;
import com.tickify.booking.dto.UserBookingsDto;
import com.tickify.booking.entity.Booking;
import com.tickify.booking.entity.QRCode;
import com.tickify.booking.mapper.BookingMapper;
import com.tickify.booking.repository.BookingRepository;
import com.tickify.booking.repository.QRCodeRepository;
import com.tickify.config.RabbitMQConfig;
import com.tickify.user.entity.User;
import com.tickify.user.repository.UserRepository;
import com.tickify.util.EventBus;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final QueueService queueService;
    private final SeatLockService seatLockService;
    private final PendingBookingWriter pendingBookingWriter;
    private final BookingRepository bookingRepo;
    private final UserRepository userRepo;
    private final EventBus eventBus;
    private final BookingMapper bookingMapper;
    private final QRCodeRepository qrCodeRepo;

    /** A user may only reach the seat map through the waiting room. */
    public void ensureActiveSlot(UUID eventId, UUID userId) {
        if (!queueService.hasActiveSlot(eventId, userId)) {
            throw new AccessDeniedException("You do not hold an active booking slot for this event");
        }
    }

    /**
     * Lock the requested seats and open a booking for them.
     *
     * <p>The two steps belong together because of what happens when the second fails: the
     * Redis locks taken by the first would otherwise hold those seats until their TTL for a
     * booking that does not exist. The database work sits in {@link PendingBookingWriter} so
     * its transaction does not span the Redis round-trips — and so that it goes through a
     * Spring proxy, which a call to a {@code @Transactional} method on {@code this} would not.
     */
    public UUID lockSeatsAndCreateBooking(LockSeatRequestDto request, UUID userId) {
        lockSeats(request, userId);
        try {
            return pendingBookingWriter.createPendingBooking(request, userId);
        } catch (RuntimeException e) {
            request.seatIds().forEach(seatId ->
                    seatLockService.releaseSeat(request.eventId(), seatId, userId));
            throw e;
        }
    }

    /**
     * Take a Redis lock on each requested seat, all-or-nothing.
     *
     * <p>If any seat is already taken the locks acquired earlier in this call are handed back,
     * using the owner-checked release so a partially-failed request can never free a seat that
     * belongs to somebody else.
     */
    public void lockSeats(LockSeatRequestDto request, UUID userId) {
        List<UUID> acquired = new ArrayList<>(request.seatIds().size());

        for (UUID seatId : request.seatIds()) {
            if (seatLockService.tryLockSeat(request.eventId(), seatId, userId)) {
                acquired.add(seatId);
                continue;
            }

            acquired.forEach(s -> seatLockService.releaseSeat(request.eventId(), s, userId));
            log.debug("Seat {} contended for event {}; released {} partial lock(s) for user {}",
                    seatId, request.eventId(), acquired.size(), userId);
            throw new IllegalStateException("Seat already locked: " + seatId);
        }
    }

    /**
     * Hand the booking to the payment service over RabbitMQ and return immediately.
     * The HTTP request does not wait for the payment provider; the client polls the booking.
     */
    public void initiatePayment(UUID bookingId, UUID userId) {
        var booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (!booking.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your booking");
        }

        var msg = new PaymentRequestDto(booking.getId(), userId, booking.getBillingAmount(), "USD");
        eventBus.publish(RabbitMQConfig.RK_PAYMENT_REQUESTED, msg);

        log.debug("Published payment.requested for booking {} (amount {})",
                booking.getId(), booking.getBillingAmount());
    }

    @Transactional(readOnly = true)
    public UserBookingsDto getUserBookings(UUID loggedInUserId) {
        User user = userRepo.findById(loggedInUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        List<BookingDto> bookingDtos = bookingRepo.findAllByUser(user).stream()
                .map(booking -> {
                    QRCode qrCode = qrCodeRepo.findByBooking_Id(booking.getId()).orElse(null);
                    return bookingMapper.entityToDto(booking, qrCode);
                })
                .toList();

        return new UserBookingsDto(user.getId(), user.getEmail(), bookingDtos);
    }

    /** Single booking lookup, used by the checkout screen to poll for the payment outcome. */
    @Transactional(readOnly = true)
    public BookingDto getBooking(UUID bookingId, UUID loggedInUserId) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (!booking.getUser().getId().equals(loggedInUserId)) {
            throw new AccessDeniedException("Not your booking");
        }

        return bookingMapper.entityToDto(booking, qrCodeRepo.findByBooking_Id(bookingId).orElse(null));
    }
}

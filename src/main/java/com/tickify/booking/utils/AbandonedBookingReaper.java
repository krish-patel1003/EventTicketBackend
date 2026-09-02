package com.tickify.booking.utils;

import com.tickify.booking.entity.Booking;
import com.tickify.booking.entity.PaymentStatus;
import com.tickify.booking.repository.BookingRepository;
import com.tickify.booking.service.SeatLockService;
import com.tickify.config.properties.TickifyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Puts abandoned checkouts back on sale.
 *
 * <p>A Redis seat lock expires on its own, which is what makes a closed browser tab
 * self-healing. The {@code booking_seats} rows behind it do not: they keep claiming the seat,
 * so the next buyer's insert collides with the live-claim index and the seat is stuck. The
 * Redis TTL and the database claim have to expire together, and this is the half that needs
 * a sweeper.
 *
 * <p>Bookings are given a grace period beyond the lock TTL so a payment still in flight —
 * the provider is slow, the queue is backed up — is never reaped out from under itself.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbandonedBookingReaper {

    private static final int BATCH_LIMIT = 500;

    private final BookingRepository bookingRepository;
    private final SeatLockService seatLockService;
    private final TickifyProperties properties;

    @Scheduled(fixedDelayString = "${tickify.booking.reaper-interval-ms:60000}", initialDelay = 30_000)
    @Transactional
    public void releaseAbandonedBookings() {
        Instant cutoff = Instant.now()
                .minus(properties.getSeatLock().getTtl())
                .minus(properties.getBooking().getReaperGracePeriod());

        List<Booking> abandoned = bookingRepository
                .findStalePendingBookings(PaymentStatus.PENDING, cutoff, Limit.of(BATCH_LIMIT));

        if (abandoned.isEmpty()) {
            return;
        }

        Instant releasedAt = Instant.now();
        int seatsReleased = 0;

        for (Booking booking : abandoned) {
            booking.setPaymentStatus(PaymentStatus.FAILED);

            for (var bookingSeat : booking.getSeats()) {
                if (bookingSeat.isReleased()) {
                    continue;
                }
                bookingSeat.release(releasedAt);
                seatLockService.releaseSeat(booking.getEvent().getId(), bookingSeat.getSeat().getId());
                seatsReleased++;
            }
        }

        bookingRepository.saveAll(abandoned);

        log.info("Reaped {} abandoned booking(s), releasing {} seat(s) back on sale",
                abandoned.size(), seatsReleased);
    }
}

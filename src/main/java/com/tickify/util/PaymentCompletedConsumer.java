package com.tickify.util;

import com.tickify.booking.dto.BookingConfirmedDto;
import com.tickify.booking.dto.PaymentCompletedDto;
import com.tickify.booking.entity.Booking;
import com.tickify.booking.entity.EntryMethodType;
import com.tickify.booking.entity.PaymentStatus;
import com.tickify.booking.entity.QRCode;
import com.tickify.booking.repository.BookingRepository;
import com.tickify.booking.repository.QRCodeRepository;
import com.tickify.booking.service.SeatLockService;
import com.tickify.config.RabbitMQConfig;
import com.tickify.event.repository.EventSeatRepository;
import com.tickify.observability.BookingMetrics;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Second half of the booking saga: turns a payment outcome into a confirmed ticket, or
 * releases everything the attempt was holding.
 *
 * <p>On success the seats move from "locked in Redis" to "reserved in Postgres" — the Redis
 * lock is only released once the durable reservation is written, so there is no window in
 * which a seat is neither held nor booked. On failure the locks are dropped immediately
 * rather than left to expire, putting the seats back on sale straight away.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletedConsumer {

    private final BookingRepository bookingRepository;
    private final EventSeatRepository eventSeatRepository;
    private final SeatLockService seatLockService;
    private final QRCodeRepository qrCodeRepository;
    private final EventBus eventBus;
    private final BookingMetrics metrics;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.Q_PAYMENT_COMPLETED)
    public void onPaymentCompleted(PaymentCompletedDto request) throws Exception {

        var booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (booking.getPaymentStatus() != PaymentStatus.PENDING) {
            // RabbitMQ delivers at-least-once; a redelivery must not mint a second QR code.
            log.warn("Ignoring duplicate payment.completed for booking {} already in state {}",
                    booking.getId(), booking.getPaymentStatus());
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(request.status())) {
            confirm(booking);
        } else {
            release(booking);
        }
    }

    private void confirm(Booking booking) throws Exception {
        booking.setPaymentStatus(PaymentStatus.SUCCESS);
        bookingRepository.save(booking);

        List<String> seatNumbers = booking.getSeats().stream()
                .map(bs -> bs.getSeat().getSeatNumber())
                .toList();

        booking.getSeats().forEach(bs -> {
            var seat = bs.getSeat();
            seat.setReserved(true);
            eventSeatRepository.save(seat);
            seatLockService.releaseSeat(booking.getEvent().getId(), seat.getId());
        });

        String qrCodeText = generateAndSaveQRCode(booking);

        eventBus.publish(RabbitMQConfig.RK_BOOKING_CONFIRMED, new BookingConfirmedDto(
                booking.getId(),
                booking.getUser().getId(),
                booking.getEvent().getId(),
                seatNumbers,
                qrCodeText
        ));

        metrics.bookingConfirmed();
        log.info("Confirmed booking {} ({}) with seats {}",
                booking.getId(), booking.getBookingReference(), seatNumbers);
    }

    private void release(Booking booking) {
        booking.setPaymentStatus(PaymentStatus.FAILED);

        // Give the seats up in both places. Dropping only the Redis lock puts the seat back
        // on sale while its booking_seats row still claims it, so the next buyer's insert
        // collides with the live-claim index and fails — the seat becomes unsellable.
        Instant releasedAt = Instant.now();
        booking.getSeats().forEach(bookingSeat -> {
            bookingSeat.release(releasedAt);
            seatLockService.releaseSeat(booking.getEvent().getId(), bookingSeat.getSeat().getId());
        });

        bookingRepository.save(booking);

        metrics.bookingFailed();
        log.info("Released booking {} ({}) after failed payment; {} seat(s) back on sale",
                booking.getId(), booking.getBookingReference(), booking.getSeats().size());
    }

    private String generateAndSaveQRCode(Booking booking) throws Exception {
        String base64Qr = QRCodeUtil.generateQRCodeBase64(booking.getBookingReference(), 250, 250);

        QRCode qr = new QRCode();
        qr.setBooking(booking);
        qr.setQrCode(base64Qr);
        qr.setMethod(EntryMethodType.SCAN);
        qrCodeRepository.save(qr);

        return base64Qr;
    }
}

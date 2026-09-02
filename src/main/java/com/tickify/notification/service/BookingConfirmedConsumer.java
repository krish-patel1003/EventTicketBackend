package com.tickify.notification.service;

import com.tickify.booking.dto.BookingConfirmedDto;
import com.tickify.booking.entity.Booking;
import com.tickify.booking.repository.BookingRepository;
import com.tickify.config.RabbitMQConfig;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Sends the confirmation e-mail once a booking is paid.
 *
 * <p>Deliberately best-effort. The ticket is already valid at the gate by the time this runs,
 * so a mail outage must not fail the booking: every exception is caught and logged rather than
 * thrown. Letting it propagate would nack the message, RabbitMQ would redeliver it immediately,
 * and one unsendable address would spin the consumer forever — which is exactly what happened
 * before the entities below were loaded inside a transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingConfirmedConsumer {

    private static final DateTimeFormatter EVENT_DATE =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'at' HH:mm").withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;
    private final BookingRepository bookingRepository;

    @Value("${tickify.notifications.from:no-reply@tickify.example}")
    private String fromAddress;

    /**
     * Read-only transaction: the booking is loaded with its lazy event, venue, user and seat
     * associations, all of which the template dereferences. Outside a session those reads fail
     * with a LazyInitializationException.
     */
    @Transactional(readOnly = true)
    @RabbitListener(queues = RabbitMQConfig.Q_BOOKING_CONFIRMED)
    public void onBookingConfirmed(BookingConfirmedDto dto) {
        try {
            Booking booking = bookingRepository.findById(dto.bookingId()).orElse(null);
            if (booking == null) {
                log.warn("booking.confirmed for unknown booking {}; dropping", dto.bookingId());
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(booking.getUser().getEmail());
            helper.setFrom(fromAddress);
            helper.setSubject("Your tickets — booking " + booking.getBookingReference());
            helper.setText(renderEmail(booking, dto.qrcode()), true);

            mailSender.send(message);
            log.info("Sent confirmation for booking {} to {}",
                    booking.getBookingReference(), booking.getUser().getEmail());

        } catch (Exception e) {
            // The customer can still see the QR code in the app; losing the e-mail is not
            // a reason to hold up the queue or to invalidate a paid ticket.
            log.error("Could not send confirmation e-mail for booking {}", dto.bookingId(), e);
        }
    }

    private String renderEmail(Booking booking, String qrCodeBase64) {
        String seatNumbers = booking.getSeats().stream()
                .map(bookingSeat -> bookingSeat.getSeat().getSeatNumber())
                .collect(Collectors.joining(", "));

        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8" /><title>Your Tickify tickets</title></head>
                <body style="font-family:Arial,sans-serif;margin:0;padding:20px;background:#f8f8f8;color:#333;">
                <table width="100%%" cellpadding="0" cellspacing="0" border="0"
                       style="max-width:600px;margin:auto;background:#fff;border-radius:8px;overflow:hidden;">
                  <tr>
                    <td style="padding:20px;text-align:center;background:#2f6df6;color:#fff;">
                      <h2 style="margin:0;">Booking confirmed</h2>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:20px;">
                      <p>Hi <strong>%s</strong>,</p>
                      <p>Your payment went through. Here are your ticket details:</p>
                      <table width="100%%" cellpadding="8" cellspacing="0" border="0"
                             style="border:1px solid #ddd;border-radius:6px;margin:15px 0;">
                        <tr><td><strong>Event</strong></td><td>%s</td></tr>
                        <tr><td><strong>Date</strong></td><td>%s</td></tr>
                        <tr><td><strong>Venue</strong></td><td>%s</td></tr>
                        <tr><td><strong>Booking reference</strong></td><td>%s</td></tr>
                        <tr><td><strong>Seats</strong></td><td>%s</td></tr>
                        <tr><td><strong>Amount paid</strong></td><td>$%s</td></tr>
                      </table>
                      <p>Show this code at the gate:</p>
                      <div style="text-align:center;margin:20px 0;">
                        <img src="data:image/png;base64,%s" alt="Entry QR code" width="200" height="200"
                             style="border:1px solid #ddd;padding:10px;border-radius:8px;" />
                      </div>
                      <p style="font-size:13px;color:#777;text-align:center;">
                        Each code admits one person, once. Please arrive 30 minutes before the start.
                      </p>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(
                booking.getUser().getEmail(),
                booking.getEvent().getTitle(),
                EVENT_DATE.format(booking.getEvent().getStartDate()),
                booking.getEvent().getVenue().getName(),
                booking.getBookingReference(),
                seatNumbers,
                booking.getBillingAmount(),
                qrCodeBase64
        );
    }
}

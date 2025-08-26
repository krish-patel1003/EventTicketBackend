package com.project.event_ticket_backend.notification.service;

import com.project.event_ticket_backend.booking.dto.BookingConfirmedDto;
import com.project.event_ticket_backend.booking.entity.Booking;
import com.project.event_ticket_backend.booking.repository.BookingRepository;
import com.project.event_ticket_backend.config.RabbitMQConfig;
import com.project.event_ticket_backend.event.entity.Event;
import com.project.event_ticket_backend.event.repository.EventRepository;
import com.project.event_ticket_backend.user.entity.User;
import com.project.event_ticket_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingConfirmedConsumer {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;


    @RabbitListener(queues = RabbitMQConfig.Q_BOOKING_CONFIRMED)
    public void onBookingConfirmed(BookingConfirmedDto dto) {

        User user = userRepository.getReferenceById(dto.userId());
        Event event = eventRepository.getReferenceById(dto.eventId());
        Booking booking = bookingRepository.getReferenceById(dto.bookingId());


        final var emailText = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8" />
                <title>Your Ticket Confirmation</title>
                </head>
                <body style="font-family: Arial, sans-serif; margin:0; padding:20px; background:#f8f8f8; color:#333;">

                <table width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:600px; margin:auto; background:#ffffff; border-radius:8px; overflow:hidden; box-shadow:0 2px 6px rgba(0,0,0,0.1);">
                <tr>
                <td style="padding:20px; text-align:center; background:#4CAF50; color:#fff;">
                <h2 style="margin:0;">Booking Confirmed 🎉</h2>
                </td>
                </tr>

                <tr>
                <td style="padding:20px;">
                <p>Hi <strong>{{userName}}</strong>,</p>
                <p>Thank you for your booking! Below are your ticket details:</p>

                <table width="100%" cellpadding="8" cellspacing="0" border="0" style="border:1px solid #ddd; border-radius:6px; margin:15px 0;">
                <tr>
                <td><strong>Event:</strong></td>
                <td>{{eventTitle}}</td>
                </tr>
                <tr>
                <td><strong>Date:</strong></td>
                <td>{{eventDate}}</td>
                </tr>
                <tr>
                <td><strong>Venue:</strong></td>
                <td>{{venueName}}</td>
                </tr>
                <tr>
                <td><strong>Booking Ref:</strong></td>
                <td>{{bookingReference}}</td>
                </tr>
                <tr>
                <td><strong>Seats:</strong></td>
                <td>{{seatNumbers}}</td>
                </tr>
                <tr>
                <td><strong>Amount Paid:</strong></td>
                <td>${{billingAmount}}</td>
                </tr>
                </table>

                <p style="margin-top:20px;">Present the QR code below at entry:</p>
                <div style="text-align:center; margin:20px 0;">
                <img src="data:image/png;base64,{{qrCodeBase64}}" alt="QR Code" width="200" height="200" style="border:1px solid #ddd; padding:10px; border-radius:8px;" />
                </div>

                <p style="font-size:13px; color:#777; text-align:center; margin-top:20px;">
                Please arrive at least 30 minutes before the event starts.
                This ticket is personal and non-transferable.
                </p>
                </td>
                </tr>
                </table>

                </body>
                </html>
        """.formatted(
                user.getEmail(),
                event.getTitle(),
                event.getStartDate(),
                event.getVenue().getName(),
                booking.getBookingReference(),
                booking.getSeats().stream().map(
                        s -> s.getSeat().getSeatNumber()).toList(),
                booking.getBillingAmount(),
                dto.qrcode()
        );

        final var message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Ticket Confirmation Booking Reference: #" + booking.getBookingReference());
        message.setFrom("System");
        message.setText(emailText);

        mailSender.send(message);
    }
}

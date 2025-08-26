package com.project.event_ticket_backend.util;

import com.project.event_ticket_backend.booking.dto.BookingConfirmedDto;
import com.project.event_ticket_backend.booking.dto.PaymentCompletedDto;
import com.project.event_ticket_backend.booking.entity.Booking;
import com.project.event_ticket_backend.booking.entity.EntryMethodType;
import com.project.event_ticket_backend.booking.entity.PaymentStatus;
import com.project.event_ticket_backend.booking.entity.QRCode;
import com.project.event_ticket_backend.booking.repository.BookingRepository;
import com.project.event_ticket_backend.booking.repository.QRCodeRepository;
import com.project.event_ticket_backend.booking.service.SeatLockService;
import com.project.event_ticket_backend.config.RabbitMQConfig;
import com.project.event_ticket_backend.event.repository.EventSeatRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentCompletedConsumer {

    private final BookingRepository bookingRepository;
    private final EventSeatRepository eventSeatRepository;
    private final SeatLockService seatLockService;
    private final QRCodeRepository qrCodeRepository;
    private final EventBus eventBus;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.RK_PAYMENT_COMPLETED)
    public void onPaymentCompleted(PaymentCompletedDto request) throws Exception {

        var booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if("SUCCESS".equalsIgnoreCase(request.status())) {
            // reserve seats in DB and release locks;
            booking.setPaymentStatus(PaymentStatus.SUCCESS);
            bookingRepository.save(booking);

            List<String> seatNumbers = booking.getSeats().stream()
                    .map(bs -> bs.getSeat().getSeatNumber()).toList();

            // mark seats as reserved
            booking.getSeats().forEach(bs -> {
                var seat = bs.getSeat();
                seat.setReserved(true);
                eventSeatRepository.save(seat);
                seatLockService.releaseSeat(booking.getEvent().getId(), seat.getId());
            });

            // generate + persist QR
            String qrCodeText = generateAndSaveQRCode(booking);

            // publish booking.confirmed for Notification
            var out = new BookingConfirmedDto(
                    booking.getId(),
                    booking.getUser().getId(),
                    booking.getEvent().getId(),
                    seatNumbers,
                    qrCodeText
            );

            eventBus.publish(RabbitMQConfig.RK_BOOKING_CONFIRMED, out);
        }

        else {
            booking.setPaymentStatus(PaymentStatus.FAILED);
            bookingRepository.save(booking);

            // release locks
            booking.getSeats().forEach(
                    bs -> seatLockService.releaseSeat(
                            booking.getEvent().getId(), bs.getSeat().getId()));
        }

    }

    private String generateAndSaveQRCode(Booking booking) throws Exception {
        String qrContent = booking.getBookingReference(); // could also include user + event
        String base64Qr =  QRCodeUtil.generateQRCodeBase64(qrContent, 250, 250);

        QRCode qr = new QRCode();
        qr.setBooking(booking);
        qr.setQrCode(base64Qr);  // stored in TEXT column
        qr.setMethod(EntryMethodType.SCAN);
        qrCodeRepository.save(qr);

        return base64Qr;
    }

}

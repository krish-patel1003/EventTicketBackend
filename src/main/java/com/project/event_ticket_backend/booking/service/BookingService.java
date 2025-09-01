package com.project.event_ticket_backend.booking.service;

import com.project.event_ticket_backend.booking.dto.BookingDto;
import com.project.event_ticket_backend.booking.dto.LockSeatRequestDto;
import com.project.event_ticket_backend.booking.dto.PaymentRequestDto;
import com.project.event_ticket_backend.booking.dto.UserBookingsDto;
import com.project.event_ticket_backend.booking.entity.Booking;
import com.project.event_ticket_backend.booking.entity.BookingSeat;
import com.project.event_ticket_backend.booking.entity.PaymentStatus;
import com.project.event_ticket_backend.booking.entity.QRCode;
import com.project.event_ticket_backend.booking.mapper.BookingMapper;
import com.project.event_ticket_backend.booking.repository.BookingRepository;
import com.project.event_ticket_backend.booking.repository.BookingSeatRepository;
import com.project.event_ticket_backend.booking.repository.QRCodeRepository;
import com.project.event_ticket_backend.config.RabbitMQConfig;
import com.project.event_ticket_backend.event.entity.TicketType;
import com.project.event_ticket_backend.event.repository.EventRepository;
import com.project.event_ticket_backend.event.repository.EventSeatRepository;
import com.project.event_ticket_backend.event.repository.TicketTypeRepository;
import com.project.event_ticket_backend.user.entity.User;
import com.project.event_ticket_backend.user.repository.UserRepository;
import com.project.event_ticket_backend.util.EventBus;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final QueueService queueService;
    private final SeatLockService seatLockService;
    private final BookingRepository bookingRepo;
    private final EventSeatRepository eventSeatRepo;
    private final BookingSeatRepository bookingSeatRepo;
    private final EventRepository eventRepo;
    private final TicketTypeRepository ticketTypeRepo;
    private final UserRepository userRepo;
    private final EventBus eventBus;
    private final BookingMapper bookingMapper;
    private final QRCodeRepository qrCodeRepo;

    public void ensureActiveSlot(UUID eventId, UUID userId) throws Exception{
        if (!queueService.hasActiveSlot(eventId, userId)) {
            throw new AccessDeniedException("Not Active in queue yet");
        }
    }

    @Transactional
    public void lockSeats(LockSeatRequestDto request, UUID userId) {
        for (UUID seatId : request.seatIds()) {
            // On failure, release any partial locks you obtained (best-effort)
            if (!seatLockService.tryLockSeat(request.eventId(), seatId, userId)) {
                request.seatIds().forEach(s -> seatLockService.releaseSeat(request.eventId(), s));
                throw new IllegalStateException("Seat already locked: " + seatId);
            }
        }
    }

    @Transactional
    public UUID createPendingBooking(LockSeatRequestDto request, UUID userId) {

        for (UUID seatId : request.seatIds()) {
            // verify locks belong to this user
            if (!seatLockService.isLockedBy(request.eventId(), seatId, userId)) {
                throw new IllegalStateException("Lock missing for seat: " + seatId);
            }
        }

        var user = new User();
        user.setId(userId);

        var event = eventRepo.getReferenceById(request.eventId());
        var ticketType = ticketTypeRepo.getReferenceById(request.ticketTypeId());

        var booking = new Booking();
        booking.setUser(user);
        booking.setEvent(event);
        booking.setTicketType(ticketType);
        booking.setBookingReference(generateRef());
        booking.setPaymentStatus(PaymentStatus.PENDING);

        BigDecimal billingAmt = BigDecimal.valueOf(ticketType.getPrice() * request.seatIds().size());

        booking.setBillingAmount(billingAmt);

        // Attach seats

        for (UUID sId : request.seatIds()) {
            var seat = eventSeatRepo.getReferenceById(sId);
            var bs = new BookingSeat();
            bs.setBooking(booking);
            bs.setSeat(seat);
            bookingSeatRepo.save(bs);
        }

        return booking.getId();
    }

    public void initiatePayment(UUID bookingId, UUID userId) throws Exception{
        var booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not Found"));

        if (!booking.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not Your Booking");
        }

        var msg = new PaymentRequestDto(booking.getId(), userId, booking.getBillingAmount(), "USD");
        eventBus.publish(RabbitMQConfig.RK_PAYMENT_REQUESTED, msg);
    }

    private String generateRef() {
        return "TICK - " + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public UserBookingsDto getUserBookings(UUID loggedInUserId) {
        User user = userRepo.findById(loggedInUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        List<Booking> bookings = bookingRepo.findAllByUser(user);

        List<BookingDto> bookingDtos = bookings.stream()
                .map(booking -> {
                    QRCode qrCode = qrCodeRepo.findByBooking_Id(booking.getId()).orElse(null);
                    return bookingMapper.entityToDto(booking, qrCode);
                })
                .toList();

        return new UserBookingsDto(
                user.getId(),
                user.getEmail(),
                bookingDtos
        );
    }
}

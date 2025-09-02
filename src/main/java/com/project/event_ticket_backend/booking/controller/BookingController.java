package com.project.event_ticket_backend.booking.controller;

import com.project.event_ticket_backend.booking.dto.LockSeatRequestDto;
import com.project.event_ticket_backend.booking.dto.LockSeatResponseDto;
import com.project.event_ticket_backend.booking.dto.StartPaymentRequestDto;
import com.project.event_ticket_backend.booking.dto.UserBookingsDto;
import com.project.event_ticket_backend.booking.service.BookingService;
import com.project.event_ticket_backend.booking.service.QueueService;
import com.project.event_ticket_backend.user.entity.User;
import com.project.event_ticket_backend.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class BookingController {

    private final QueueService queueService;
    private final BookingService bookingService;
    private final UserRepository userRepository;

    private User getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new EntityNotFoundException("No authenticated user found");
        }

        String email = authentication.getName(); // from UserDetails.getUsername()
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Logged-in user not found"));
    }

    @PostMapping("/lock")
    public ResponseEntity<?> lock(@RequestBody LockSeatRequestDto req) throws Exception {
        User user = getLoggedInUser();
        UUID userId = user.getId();

        // Ensure active queue slot
        bookingService.ensureActiveSlot(req.eventId(), userId);

        // Lock requested seats
        bookingService.lockSeats(req, userId);

        // Start booking process
        UUID bookingId = bookingService.createPendingBooking(req, userId);

        // Response
        LockSeatResponseDto response = new LockSeatResponseDto(
                bookingId, req.eventId(), req.seatIds(), req.ticketTypeId(), true
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/payment/initiate")
    public ResponseEntity<?> initiate(@RequestBody StartPaymentRequestDto req) throws Exception {
        User user = getLoggedInUser();
        UUID userId = user.getId();

        bookingService.initiatePayment(req.bookingId(), userId);
        return ResponseEntity.ok(Map.of("status", "PAYMENT_REQUESTED"));
    }

    @PostMapping("/my-bookings")
    public ResponseEntity<?> userBookings() {
        User user = getLoggedInUser();
        UUID userId = user.getId();

        UserBookingsDto response = bookingService.getUserBookings(userId);
        return ResponseEntity.ok(response);
    }
}

package com.project.event_ticket_backend.booking.controller;

import com.project.event_ticket_backend.booking.dto.LockSeatRequestDto;
import com.project.event_ticket_backend.booking.dto.LockSeatResponseDto;
import com.project.event_ticket_backend.booking.dto.StartPaymentRequestDto;
import com.project.event_ticket_backend.booking.service.BookingService;
import com.project.event_ticket_backend.booking.service.QueueService;
import com.project.event_ticket_backend.user.service.JpaUserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class BookingController {

    private final QueueService queueService;
    private final BookingService bookingService;

    @PostMapping("/lock")
    public ResponseEntity<?> lock(@RequestBody LockSeatRequestDto req,
                                  @AuthenticationPrincipal JpaUserDetailsImpl loggedInUser) throws Exception {

        UUID loggedInUserId = loggedInUser.getUser().getId();

        // Lock Seat
        bookingService.ensureActiveSlot(req.eventId(), loggedInUserId);
        bookingService.lockSeats(req, loggedInUserId);


        // Start booking
        UUID bookingId = bookingService.createPendingBooking(req, loggedInUserId);

        // Response
        LockSeatResponseDto response = new LockSeatResponseDto(
                bookingId, req.eventId(), req.seatIds(), req.ticketTypeId(), true);

        return ResponseEntity.ok(response);
    }

//    @PostMapping("/start")
//    public ResponseEntity<?> start(@RequestBody LockSeatRequestDto req,
//                                   @AuthenticationPrincipal JpaUserDetailsImpl loggedInUser) {
//
//        UUID loggedInUserId = loggedInUser.getUser().getId();
//
//        return ResponseEntity.ok();
//    }

    @PostMapping("/payment/initiate")
    public ResponseEntity<?> initiate(@RequestBody StartPaymentRequestDto req,
                                      @AuthenticationPrincipal JpaUserDetailsImpl loggedInUser) throws Exception {

        UUID loggedInUserId = loggedInUser.getUser().getId();
        bookingService.initiatePayment(req.bookingId(), loggedInUserId);
        return ResponseEntity.ok(Map.of("status", "PAYMENT_REQUESTED"));
    }
}

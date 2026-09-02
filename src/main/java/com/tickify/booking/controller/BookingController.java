package com.tickify.booking.controller;

import com.tickify.booking.dto.BookingDto;
import com.tickify.booking.dto.LockSeatRequestDto;
import com.tickify.booking.dto.LockSeatResponseDto;
import com.tickify.booking.dto.StartPaymentRequestDto;
import com.tickify.booking.dto.UserBookingsDto;
import com.tickify.booking.service.BookingService;
import com.tickify.user.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Tag(name = "Bookings", description = "Seat locking, checkout and ticket retrieval")
public class BookingController {

    private final BookingService bookingService;
    private final CurrentUserService currentUser;

    @Operation(summary = "Lock seats and open a pending booking",
            description = "Requires an active waiting-room slot. Locks every requested seat in Redis "
                    + "all-or-nothing, then creates a PENDING booking.")
    @PostMapping("/lock")
    public ResponseEntity<LockSeatResponseDto> lock(@RequestBody LockSeatRequestDto req) {
        UUID userId = currentUser.requireId();

        bookingService.ensureActiveSlot(req.eventId(), userId);
        UUID bookingId = bookingService.lockSeatsAndCreateBooking(req, userId);

        return ResponseEntity.ok(new LockSeatResponseDto(
                bookingId, req.eventId(), req.seatIds(), req.ticketTypeId(), true));
    }

    @Operation(summary = "Start payment for a pending booking",
            description = "Publishes payment.requested and returns immediately. "
                    + "Poll GET /api/v1/bookings/{id} for the outcome.")
    @PostMapping("/payment/initiate")
    public ResponseEntity<Map<String, String>> initiate(@RequestBody StartPaymentRequestDto req) {
        bookingService.initiatePayment(req.bookingId(), currentUser.requireId());
        return ResponseEntity.ok(Map.of("status", "PAYMENT_REQUESTED"));
    }

    @Operation(summary = "Get one booking, including its QR code once confirmed")
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingDto> getBooking(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(bookingService.getBooking(bookingId, currentUser.requireId()));
    }

    @Operation(summary = "List the caller's bookings")
    @GetMapping("/my-bookings")
    public ResponseEntity<UserBookingsDto> userBookings() {
        return ResponseEntity.ok(bookingService.getUserBookings(currentUser.requireId()));
    }

    /** @deprecated retained for the original API shape; use {@code GET /my-bookings}. */
    @Deprecated
    @Operation(summary = "List the caller's bookings (deprecated POST form)")
    @PostMapping("/my-bookings")
    public ResponseEntity<UserBookingsDto> userBookingsLegacy() {
        return userBookings();
    }
}

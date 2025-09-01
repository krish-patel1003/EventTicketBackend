package com.project.event_ticket_backend.booking.dto;

import java.util.List;
import java.util.UUID;

public record UserBookingsDto(
        UUID userId,
        String email,
        List<BookingDto> bookingsList
) {
}

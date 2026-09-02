package com.tickify.booking.dto;

import java.util.List;
import java.util.UUID;

public record UserBookingsDto(
        UUID userId,
        String email,
        List<BookingDto> bookingsList
) {
}

package com.tickify.booking.dto;

import java.time.Instant;
import java.util.UUID;

public record BookingEventDto(
        UUID id,
        String title,
        String venue,
        Instant startDate,
        Instant endDate
) {
}

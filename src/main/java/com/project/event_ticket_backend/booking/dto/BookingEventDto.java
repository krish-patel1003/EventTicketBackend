package com.project.event_ticket_backend.booking.dto;

import java.time.Instant;
import java.util.UUID;

public record BookingEventDto(
        UUID id,
        String title,
        Instant startDate,
        Instant endDate
) {
}

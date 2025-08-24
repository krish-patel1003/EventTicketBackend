package com.project.event_ticket_backend.booking.dto;

import java.util.UUID;

public record BookingSeatDto(
        UUID seatId,
        String seatNumber,
        String rowLabel,
        String section
) {
}

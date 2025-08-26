package com.project.event_ticket_backend.booking.dto;

import java.util.List;
import java.util.UUID;

public record LockSeatResponseDto(
        UUID bookingId,
        UUID eventId,
        List<UUID> seatIds,
        UUID ticketTypeId,
        boolean locked
) {
}

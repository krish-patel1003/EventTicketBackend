package com.project.event_ticket_backend.event.dto;

import java.util.UUID;

public record EventSeatDto(
        UUID event_id,
        String event,
        String seatNumber,
        String ticketType,
        String rowLabel,
        String section,
        boolean isLocked,
        boolean isReserved
) {
}

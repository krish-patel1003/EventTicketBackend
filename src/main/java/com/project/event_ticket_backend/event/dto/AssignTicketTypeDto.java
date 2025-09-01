package com.project.event_ticket_backend.event.dto;

import java.util.UUID;

public record AssignTicketTypeDto(
        UUID eventId,
        UUID ticketTypeId,
        String section,
        String rowLabel,
        UUID seatId
) {
}

package com.project.event_ticket_backend.booking.dto;

import com.project.event_ticket_backend.event.entity.TicketType;

import java.util.List;
import java.util.UUID;

public record LockSeatRequestDto(
        UUID eventId,
        List<UUID> seatIds,
        UUID ticketTypeId
) {
}

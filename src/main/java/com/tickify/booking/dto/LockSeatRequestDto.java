package com.tickify.booking.dto;

import com.tickify.event.entity.TicketType;

import java.util.List;
import java.util.UUID;

public record LockSeatRequestDto(
        UUID eventId,
        List<UUID> seatIds,
        UUID ticketTypeId
) {
}

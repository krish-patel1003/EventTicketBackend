package com.project.event_ticket_backend.booking.dto;

import java.util.UUID;

public record BookingTicketTypeDto(
        UUID id,
        String title
) {
}

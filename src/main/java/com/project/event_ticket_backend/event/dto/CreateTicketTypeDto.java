package com.project.event_ticket_backend.event.dto;

import java.util.UUID;

public record CreateTicketTypeDto(
        String title,
        String description,
        UUID event_id,
        double price,
        int totalQuantity
) {
}

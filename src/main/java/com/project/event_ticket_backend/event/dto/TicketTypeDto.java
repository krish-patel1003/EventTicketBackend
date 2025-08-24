package com.project.event_ticket_backend.event.dto;

import java.util.UUID;

public record TicketTypeDto(
        UUID id,
        String title,
        String description,
        double price,
        int totalQuantity,
        int availableQuantity
) {
}

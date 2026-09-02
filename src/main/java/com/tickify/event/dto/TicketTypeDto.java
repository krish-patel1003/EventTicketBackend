package com.tickify.event.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketTypeDto(
        UUID id,
        String title,
        String description,
        BigDecimal price,
        int totalQuantity,
        int availableQuantity
) {
}

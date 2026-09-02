package com.tickify.event.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTicketTypeDto(
        String title,
        String description,
        UUID event_id,
        BigDecimal price,
        int totalQuantity
) {
}

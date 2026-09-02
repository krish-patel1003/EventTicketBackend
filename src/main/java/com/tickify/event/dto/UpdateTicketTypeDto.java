package com.tickify.event.dto;

import java.math.BigDecimal;

public record UpdateTicketTypeDto(
        String title,
        String description,
        BigDecimal price,
        int totalQuantity){
}

package com.tickify.event.dto;

import java.util.UUID;

public record CreateEventDto(
        String title,
        String description,
        UUID venue_id,
        String startDate,
        String endDate,
        String ticketSaleStartDate,
        String ticketSaleEndDate
) {
}

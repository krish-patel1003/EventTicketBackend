package com.project.event_ticket_backend.event.dto;

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

package com.project.event_ticket_backend.event.dto;

import java.util.UUID;

public record UpdateEventDto(
        String title,
        String description,
        String startDate,
        String endDate,
        String ticketSaleStartDate,
        String ticketSaleEndDate
) {
}

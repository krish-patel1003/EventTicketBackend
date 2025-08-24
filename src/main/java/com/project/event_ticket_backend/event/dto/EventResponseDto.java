package com.project.event_ticket_backend.event.dto;

import com.project.event_ticket_backend.event.entity.TicketType;

import java.util.List;
import java.util.UUID;

public record EventResponseDto(
        UUID id,
        String title,
        String description,
        UUID organizerId,
        UUID venueId,
        String startDate,
        String endDate,
        String ticketSaleStartDate,
        String ticketSaleEndDate,
        List<TicketTypeDto> ticketTypeList,
        boolean isActive
) {
}

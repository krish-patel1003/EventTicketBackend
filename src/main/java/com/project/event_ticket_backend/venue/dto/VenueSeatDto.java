package com.project.event_ticket_backend.venue.dto;

public record VenueSeatDto(
        String seatNumber,
        String rowLabel,
        String section
) {
}

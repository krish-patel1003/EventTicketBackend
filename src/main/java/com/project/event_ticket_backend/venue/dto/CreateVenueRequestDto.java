package com.project.event_ticket_backend.venue.dto;

public record CreateVenueRequestDto(
        String name,
        String location,
        int totalRows,
        int seatsPerRow,
        String rowPrefix
) {
}

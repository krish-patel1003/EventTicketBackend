package com.project.event_ticket_backend.venue.dto;

import java.time.Instant;
import java.util.UUID;

public record VenueDto(
        UUID id,
        String name,
        String location,
        Instant createdAt
) {
}

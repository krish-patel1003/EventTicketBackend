package com.tickify.venue.dto;

import java.time.Instant;
import java.util.UUID;

public record VenueDto(
        UUID id,
        String name,
        String location,
        /** How many seats this venue holds — the capacity an event here will inherit. */
        long seatCount,
        Instant createdAt
) {
}

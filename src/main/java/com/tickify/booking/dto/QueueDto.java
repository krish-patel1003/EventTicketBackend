package com.tickify.booking.dto;

import java.util.Objects;
import java.util.UUID;

public record QueueDto(
        UUID eventId,
        UUID userId,
        Long position,
        boolean active
) {
}

package com.tickify.booking.dto;

import java.util.UUID;

public record BookingSeatDto(
        UUID seatId,
        String seatNumber,
        String rowLabel,
        String section
) {
}

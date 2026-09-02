package com.tickify.booking.dto;

import java.util.UUID;

public record BookingTicketTypeDto(
        UUID id,
        String title
) {
}

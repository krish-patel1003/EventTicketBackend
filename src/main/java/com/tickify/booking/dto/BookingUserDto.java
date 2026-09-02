package com.tickify.booking.dto;

import java.util.UUID;

public record BookingUserDto(
        UUID id,
        String email
) {
}

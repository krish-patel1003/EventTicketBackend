package com.tickify.booking.dto;

import java.util.UUID;

public record StartPaymentRequestDto(
        UUID bookingId
) {
}

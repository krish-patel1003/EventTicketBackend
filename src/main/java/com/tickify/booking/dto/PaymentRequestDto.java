package com.tickify.booking.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestDto(
        UUID bookingId,
        UUID userId,
        BigDecimal amount,
        String currency
) {
}

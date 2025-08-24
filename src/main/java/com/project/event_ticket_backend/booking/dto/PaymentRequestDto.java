package com.project.event_ticket_backend.booking.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestDto(
        UUID bookingId,
        UUID userId,
        BigDecimal amount,
        String currency
) {
}

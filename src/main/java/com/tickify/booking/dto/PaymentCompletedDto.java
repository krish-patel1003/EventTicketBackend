package com.tickify.booking.dto;

import java.util.UUID;

public record PaymentCompletedDto(
        UUID bookingId,
        String status,
        String transactionId
) {
}

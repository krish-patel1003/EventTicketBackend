package com.project.event_ticket_backend.booking.dto;

import java.util.UUID;

public record PaymentCompletedDto(
        UUID bookingId,
        String status,
        String transactionId
) {
}

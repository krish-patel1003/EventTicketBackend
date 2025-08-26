package com.project.event_ticket_backend.booking.dto;

import java.util.List;
import java.util.UUID;

public record BookingConfirmedDto(
        UUID bookingId,
        UUID userId,
        UUID eventId,
        List<String> seatNumbers,
        String qrcode
) {
}

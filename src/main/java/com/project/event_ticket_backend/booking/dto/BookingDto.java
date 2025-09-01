package com.project.event_ticket_backend.booking.dto;

import com.project.event_ticket_backend.booking.entity.PaymentStatus;
import com.project.event_ticket_backend.event.dto.TicketTypeDto;
import com.project.event_ticket_backend.user.dto.UserProfileResponseDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BookingDto(
        UUID id,
        String bookingReference,
        BookingUserDto user,
        BookingEventDto event,
        BookingTicketTypeDto ticketType,
        String paymentStatus,
        BigDecimal billingAmount,
        List<BookingSeatDto> seats,
        String qrCode
) {}


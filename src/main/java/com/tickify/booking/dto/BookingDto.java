package com.tickify.booking.dto;

import com.tickify.booking.entity.PaymentStatus;
import com.tickify.event.dto.TicketTypeDto;
import com.tickify.user.dto.UserProfileResponseDto;

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


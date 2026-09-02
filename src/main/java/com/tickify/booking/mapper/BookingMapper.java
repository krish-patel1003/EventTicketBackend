package com.tickify.booking.mapper;

import com.tickify.booking.dto.*;
import com.tickify.booking.entity.Booking;
import com.tickify.booking.entity.BookingSeat;
import com.tickify.booking.entity.PaymentStatus;
import com.tickify.booking.entity.QRCode;
import com.tickify.event.entity.Event;
import com.tickify.event.entity.TicketType;
import com.tickify.user.entity.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class BookingMapper {

    public BookingDto entityToDto(Booking booking, QRCode qrCode) {
        User user = booking.getUser();
        Event event = booking.getEvent();
        TicketType ticketType = booking.getTicketType();

        return new BookingDto(
                booking.getId(),
                booking.getBookingReference(),
                new BookingUserDto(user.getId(), user.getEmail()),
                new BookingEventDto(
                        event.getId(),
                        event.getTitle(),
                        event.getVenue().getName(),
                        event.getStartDate(),
                        event.getEndDate()
                ),
                new BookingTicketTypeDto(ticketType.getId(), ticketType.getTitle()),
                booking.getPaymentStatus().toString(),
                booking.getBillingAmount(),
                booking.getSeats().stream()
                        .map(bs -> new BookingSeatDto(
                                bs.getSeat().getId(),
                                bs.getSeat().getSeatNumber(),
                                bs.getSeat().getRowLabel(),
                                bs.getSeat().getSection()
                        ))
                        .toList(),
                qrCode != null ? qrCode.getQrCode() : null
        );
    }
}


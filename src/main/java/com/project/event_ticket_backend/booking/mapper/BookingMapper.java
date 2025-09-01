package com.project.event_ticket_backend.booking.mapper;

import com.project.event_ticket_backend.booking.dto.*;
import com.project.event_ticket_backend.booking.entity.Booking;
import com.project.event_ticket_backend.booking.entity.BookingSeat;
import com.project.event_ticket_backend.booking.entity.PaymentStatus;
import com.project.event_ticket_backend.booking.entity.QRCode;
import com.project.event_ticket_backend.event.entity.Event;
import com.project.event_ticket_backend.event.entity.TicketType;
import com.project.event_ticket_backend.user.entity.User;
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


package com.project.event_ticket_backend.booking.repository;

import com.project.event_ticket_backend.booking.dto.BookingDto;
import com.project.event_ticket_backend.booking.entity.Booking;
import com.project.event_ticket_backend.event.entity.Event;
import com.project.event_ticket_backend.event.entity.TicketType;
import com.project.event_ticket_backend.user.entity.User;
import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByBookingReference(String bookingReference);

    List<Booking> findAllByUser(User user);

    List<Booking> findAllByEvent(Event event);

    List<Booking> findAllByEventAndTicketType(Event event, TicketType ticketType);
}

package com.tickify.booking.repository;

import com.tickify.booking.entity.Booking;
import com.tickify.booking.entity.PaymentStatus;
import com.tickify.event.entity.Event;
import com.tickify.event.entity.TicketType;
import com.tickify.user.entity.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByBookingReference(String bookingReference);

    List<Booking> findAllByUser(User user);

    List<Booking> findAllByEvent(Event event);

    List<Booking> findAllByEventAndTicketType(Event event, TicketType ticketType);

    /**
     * Checkouts that were started and never finished, oldest first.
     *
     * <p>Seats and event are fetched eagerly: the reaper releases every seat on every booking
     * it finds, and doing that off lazy associations would issue two queries per booking.
     */
    @Query("""
            select distinct b from Booking b
            join fetch b.seats s
            join fetch s.seat
            join fetch b.event
            where b.paymentStatus = :status and b.createdAt < :cutoff
            order by b.createdAt asc
            """)
    List<Booking> findStalePendingBookings(@Param("status") PaymentStatus status,
                                           @Param("cutoff") Instant cutoff,
                                           Limit limit);
}

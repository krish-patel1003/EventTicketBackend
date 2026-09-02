package com.tickify.event.repository;

import com.tickify.event.entity.EventSeat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventSeatRepository extends JpaRepository<EventSeat, UUID> {

    // Fetch all seats for an event
    List<EventSeat> findByEvent_Id(UUID eventId);

    // Fetch available (not locked, not reserved) seats for an event
    List<EventSeat> findByEvent_IdAndIsLockedFalseAndIsReservedFalse(UUID eventId);

    /**
     * How many of these seats have already been sold.
     *
     * <p>A confirmed booking releases its Redis lock — the seat is reserved in the database,
     * so the lock has done its job — which means the lock alone no longer keeps a sold seat
     * off the market. A client holding a stale seat map can otherwise lock it again and only
     * find out at the insert.
     */
    long countByIdInAndIsReservedTrue(Collection<UUID> seatIds);

    // Fetch seats by ticket type (still useful for filtering)
    @Query("SELECT es FROM EventSeat es WHERE es.event.id = :eventId AND es.ticketType.id = :ticketTypeId")
    List<EventSeat> findByEventAndTicketType(@Param("eventId") UUID eventId,
                                             @Param("ticketTypeId") UUID ticketTypeId);

    // Modifying

    // Event Level
    @Modifying
    @Query("UPDATE EventSeat es SET es.ticketType.id = :ticketTypeId WHERE es.event.id = :eventId")
    int assignTicketTypeToAll(@Param("eventId") UUID eventId,
                              @Param("ticketTypeId") UUID ticketTypeId);

    // Section Level
    @Modifying
    @Query("UPDATE EventSeat es SET es.ticketType.id = :ticketTypeId WHERE es.event.id = :eventId AND es.section = :section")
    int assignTicketTypeBySection(@Param("eventId") UUID eventId,
                                  @Param("ticketTypeId") UUID ticketTypeId,
                                  @Param("section") String section);


    // Row Level
    @Modifying
    @Query("UPDATE EventSeat es SET es.ticketType.id = :ticketTypeId WHERE es.event.id = :eventId AND es.rowLabel = :rowLabel")
    int assignTicketTypeByRowLabel(@Param("eventId") UUID eventId,
                                  @Param("ticketTypeId") UUID ticketTypeId,
                                  @Param("rowLabel") String rowLabel);

    // Seat Level
    @Modifying
    @Query("UPDATE EventSeat es SET es.ticketType.id = :ticketTypeId WHERE es.event.id = :eventId AND es.id = :seatId")
    int assignTicketTypeBySeatId(@Param("eventId") UUID eventId,
                                   @Param("ticketTypeId") UUID ticketTypeId,
                                   @Param("seatId") UUID seatId);
}


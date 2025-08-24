package com.project.event_ticket_backend.event.repository;

import com.project.event_ticket_backend.event.entity.EventSeat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventSeatRepository extends JpaRepository<EventSeat, UUID> {

    List<EventSeat> findByEvent_Id(UUID eventId);

    @Query("SELECT es FROM EventSeat es WHERE es.event.id = :eventId AND es.ticketType.id = :ticketTypeId")
    List<EventSeat> findByEventAndTicketType(@Param("eventId") UUID eventId,
                                             @Param("ticketTypeId") UUID ticketTypeId);

    long countByEvent_Id(UUID eventId);

    long countByEvent_IdAndIsReservedTrue(UUID eventId);

    // For pagination
    @Query("SELECT es FROM EventSeat es WHERE es.event.id = :eventId")
    Page<EventSeat> findPaginatedByEventId(@Param("eventId") UUID eventId, Pageable pageable);

//    // DTO projection for performance
//    @Query("SELECT new com.yourpackage.dto.SeatSummaryDTO(es.id, es.section, es.rowLabel, es.seatNumber, es.isLocked, es.isReserved) " +
//            "FROM EventSeat es WHERE es.event.id = :eventId")
//    List<SeatSummaryDTO> fetchSeatSummaryByEventId(@Param("eventId") UUID eventId);
}


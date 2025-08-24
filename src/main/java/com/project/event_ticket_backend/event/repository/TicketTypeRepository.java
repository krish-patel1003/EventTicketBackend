package com.project.event_ticket_backend.event.repository;

import com.project.event_ticket_backend.event.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {
    List<TicketType> findByEvent_Id(UUID event_id);

    Optional<TicketType> findByTitleAndEvent_Id(String title, UUID event_id);

    @Query("SELECT t.availableQuantity FROM TicketType t WHERE t.title = :title")
    Optional<Integer> findAvailableTicketsByTicketType(@Param("title") String title);

    @Query("SELECT SUM(t.availableQuantity) FROM TicketType t WHERE t.event.id = :eventId")
    Optional<Integer> findTotalAvailableTickets(@Param("eventId") UUID eventId);
}

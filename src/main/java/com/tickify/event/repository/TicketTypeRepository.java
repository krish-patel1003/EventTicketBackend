package com.tickify.event.repository;

import com.tickify.event.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {
    List<TicketType> findByEvent_Id(UUID event_id);

    /**
     * Ticket types for a whole page of events in one query.
     *
     * <p>Mapping a page of events used to call {@link #findByEvent_Id} once per event, so
     * listing 20 events issued 21 queries. Under load that showed up as a p95 several times
     * the median on GET /api/v1/events/.
     */
    List<TicketType> findByEvent_IdIn(Collection<UUID> eventIds);

    Optional<TicketType> findByTitleAndEvent_Id(String title, UUID event_id);

    @Query("SELECT t.availableQuantity FROM TicketType t WHERE t.title = :title")
    Optional<Integer> findAvailableTicketsByTicketType(@Param("title") String title);

    @Query("SELECT SUM(t.availableQuantity) FROM TicketType t WHERE t.event.id = :eventId")
    Optional<Integer> findTotalAvailableTickets(@Param("eventId") UUID eventId);
}

package com.project.event_ticket_backend.event.repository;

import com.project.event_ticket_backend.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    Optional<Event> findByTitle(String title);
    List<Event> findAllByOrganizer_Id(UUID organizerId);
    List<Event> findAllByVenue_Id(UUID venueId);
    Page<Event> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.ticketSaleStartDate <= :now AND e.ticketSaleEndDate >= :now")
    List<Event> findOngoingSaleEvents(@Param("now") Instant now);
}

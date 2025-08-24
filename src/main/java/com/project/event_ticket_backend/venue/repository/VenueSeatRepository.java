package com.project.event_ticket_backend.venue.repository;

import com.project.event_ticket_backend.venue.dto.VenueSeatView;
import com.project.event_ticket_backend.venue.entity.VenueSeat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VenueSeatRepository extends JpaRepository<VenueSeat, UUID> {
    Page<VenueSeatView> findByVenueId(UUID venueId, Pageable pageable);
    List<VenueSeat> findByVenueId(UUID venueId);
    long countByVenueId(UUID venueId);
    Optional<VenueSeat> findByVenueIdAndSeatNumber(UUID venueId, String seatNumber);
    void deleteByVenueId(UUID venueId);

//    Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("rowLabel").ascending().and(Sort.by("seatNumber")));
//Page<VenueSeat> seatsPage = venueSeatRepository.findByVenueId(venueId, pageable);
}

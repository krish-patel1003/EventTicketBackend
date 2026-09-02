package com.tickify.venue.repository;

import com.tickify.venue.dto.VenueSeatView;
import com.tickify.venue.entity.VenueSeat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VenueSeatRepository extends JpaRepository<VenueSeat, UUID> {
    Page<VenueSeatView> findByVenueId(UUID venueId, Pageable pageable);
    List<VenueSeat> findByVenueId(UUID venueId);
    long countByVenueId(UUID venueId);

    /** Seat counts for every venue in a single query: rows of [venueId, count]. */
    @Query("select vs.venue.id, count(vs) from VenueSeat vs group by vs.venue.id")
    List<Object[]> countSeatsGroupedByVenue();
    Optional<VenueSeat> findByVenueIdAndSeatNumber(UUID venueId, String seatNumber);
    void deleteByVenueId(UUID venueId);

//    Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("rowLabel").ascending().and(Sort.by("seatNumber")));
//Page<VenueSeat> seatsPage = venueSeatRepository.findByVenueId(venueId, pageable);
}

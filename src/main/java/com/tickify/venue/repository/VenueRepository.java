package com.tickify.venue.repository;

import com.tickify.venue.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VenueRepository extends JpaRepository<Venue, UUID> {
    Optional<Venue> findByName(String name);
    List<Venue> findAllByOrderByCreatedAtDesc();
    boolean existsByName(String name);


}

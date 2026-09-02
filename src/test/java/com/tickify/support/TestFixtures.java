package com.tickify.support;

import com.tickify.venue.entity.Venue;
import com.tickify.venue.entity.VenueSeat;
import com.tickify.venue.repository.VenueRepository;
import com.tickify.venue.repository.VenueSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates the data the API offers no endpoint for.
 *
 * <p>Venues are seeded by {@code VenueSeeder} in a running system rather than created through
 * the API, so a test that needs one of a specific size writes it directly. Registered via
 * {@link Import} on the integration base class so it is only present in tests.
 */
@TestComponent
@RequiredArgsConstructor
public class TestFixtures {

    private final VenueRepository venueRepository;
    private final VenueSeatRepository venueSeatRepository;

    @Transactional
    public UUID createVenue(String name, String location, int seatCount) {
        Venue venue = new Venue();
        venue.setName(name);
        venue.setLocation(location);
        venueRepository.save(venue);

        List<VenueSeat> seats = new ArrayList<>(seatCount);
        for (int i = 1; i <= seatCount; i++) {
            VenueSeat seat = new VenueSeat();
            seat.setVenue(venue);
            seat.setSection("A");
            seat.setRowLabel("R" + ((i - 1) / 10));
            seat.setSeatNumber("A-R" + ((i - 1) / 10) + "-" + i);
            seats.add(seat);
        }
        venueSeatRepository.saveAll(seats);

        return venue.getId();
    }
}

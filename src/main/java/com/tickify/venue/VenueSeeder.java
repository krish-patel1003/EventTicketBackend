package com.tickify.venue;

import com.tickify.config.properties.TickifyProperties;
import com.tickify.venue.entity.Venue;
import com.tickify.venue.entity.VenueSeat;
import com.tickify.venue.repository.VenueRepository;
import com.tickify.venue.repository.VenueSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Creates the demo venues listed under {@code tickify.seed.venues} on first startup.
 *
 * <p>Seat layouts are generated rather than shipped as SQL so a venue of any size can be
 * produced for load testing by changing one number in configuration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VenueSeeder implements ApplicationRunner {

    private final VenueRepository venueRepository;
    private final VenueSeatRepository venueSeatRepository;
    private final TickifyProperties properties;

    private static final int TOTAL_SECTIONS = 20;
    private static final List<String> SECTION_LABELS = IntStream.rangeClosed('A', 'T')
            .mapToObj(c -> String.valueOf((char) c))
            .toList();

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getSeed().isEnabled()) {
            log.info("Venue seeding disabled (tickify.seed.enabled=false)");
            return;
        }

        properties.getSeed().getVenues()
                .forEach(v -> seedVenue(v.getName(), v.getLocation(), v.getCapacity()));
    }

    private void seedVenue(String name, String location, int capacity) {
        if (venueRepository.existsByName(name)) {
            log.info("Venue [{}] already exists. Skipping...", name);
            return;
        }

        Venue venue = new Venue();
        venue.setName(name);
        venue.setLocation(location);
        venueRepository.save(venue);

        List<VenueSeat> seats = generateLayout(venue, capacity);
        venueSeatRepository.saveAll(seats);

        log.info("Seeded venue [{}] in [{}] with [{}] seats", name, location, seats.size());
    }

    private List<VenueSeat> generateLayout(Venue venue, int totalCapacity) {
        List<VenueSeat> seatList = new ArrayList<>(totalCapacity);
        int seatsPerSection = Math.max(1, totalCapacity / TOTAL_SECTIONS);

        for (int i = 0; i < TOTAL_SECTIONS; i++) {
            String section = SECTION_LABELS.get(i);

            // Keep sections roughly square so the seat map renders sensibly at any capacity.
            int rows = Math.max(5, (int) Math.sqrt(seatsPerSection));
            int seatsPerRow = Math.max(1, seatsPerSection / rows);

            for (int row = 0; row < rows; row++) {
                String rowLabel = "R" + row;
                for (int seatNo = 1; seatNo <= seatsPerRow; seatNo++) {
                    VenueSeat seat = new VenueSeat();
                    seat.setVenue(venue);
                    seat.setRowLabel(rowLabel);
                    seat.setSection(section);
                    seat.setSeatNumber("%s-%s-%d".formatted(section, rowLabel, seatNo));
                    seatList.add(seat);
                }
            }
        }

        return seatList;
    }
}

package com.project.event_ticket_backend.venue;

import com.project.event_ticket_backend.venue.entity.Venue;
import com.project.event_ticket_backend.venue.entity.VenueSeat;
import com.project.event_ticket_backend.venue.repository.VenueRepository;
import com.project.event_ticket_backend.venue.repository.VenueSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class VenueSeeder implements ApplicationRunner {

    private final VenueRepository venueRepository;
    private final VenueSeatRepository venueSeatRepository;

    private static final int TOTAL_SECTIONS = 20;
    private static final List<String> SECTION_LABELS = IntStream.rangeClosed('A', 'T')
            .mapToObj(c -> String.valueOf((char) c ))
            .toList();


    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        seedVenue("National Stadium", "City A", 50000);
        seedVenue("Grand Arena", "City B", 100000);
    }

    private void seedVenue(String name, String location, int capacity) {
        if(venueRepository.existsByName(name)) {
            log.info("Venue [{}] already exists. Skipping...", name);
            return;
        }

        Venue venue = new Venue();
        venue.setName(name);
        venue.setLocation(location);
        venueRepository.save(venue);

        List<VenueSeat> seats = generateLayout(venue, capacity);
        venueSeatRepository.saveAll(seats);

        log.info("Seeded Venue: [{}] with [{}] seats", name, seats.size());
    }

    private List<VenueSeat> generateLayout(Venue venue, int totalCapacity) {
        List<VenueSeat> seatList = new ArrayList<>(totalCapacity);
        int seatsPerSection = totalCapacity / TOTAL_SECTIONS;

        for (int i = 0; i < TOTAL_SECTIONS; i++) {
            String section = SECTION_LABELS.get(i);


            //Estimate rows per section
            int rows = Math.max(10, (int) Math.sqrt(seatsPerSection));
            int seatsPerRow = seatsPerSection / rows;

            for (int row = 0; row < rows; row++) {
                String rowLabel = "R" + row;
                for (int seatNo = 1; seatNo <= seatsPerRow; seatNo++) {
                    VenueSeat seat = new VenueSeat();
                    seat.setVenue(venue);
                    seat.setRowLabel(rowLabel);
                    seat.setSection(section);
                    seat.setSeatNumber("%s: %s-%d".formatted(section, rowLabel, seatNo));
                    seat.setVenue(venue);
                    seatList.add(seat);
                }
            }
        }

        return seatList;

    }
}

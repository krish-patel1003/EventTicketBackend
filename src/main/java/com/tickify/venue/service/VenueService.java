package com.tickify.venue.service;

import com.tickify.venue.dto.VenueDto;
import com.tickify.venue.mapper.VenueMapper;
import com.tickify.venue.repository.VenueRepository;
import com.tickify.venue.repository.VenueSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;
    private final VenueSeatRepository venueSeatRepository;
    private final VenueMapper venueMapper;

    @Transactional(readOnly = true)
    public List<VenueDto> getAllVenues() {
        // Seat counts come from one grouped query rather than a count per venue, so the
        // list stays a fixed two queries however many venues exist.
        Map<UUID, Long> seatCounts = venueSeatRepository.countSeatsGroupedByVenue().stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        return venueRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(venue -> venueMapper.toDto(venue, seatCounts.getOrDefault(venue.getId(), 0L)))
                .toList();
    }
}

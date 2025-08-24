package com.project.event_ticket_backend.venue.service;

import com.project.event_ticket_backend.venue.dto.VenueDto;
import com.project.event_ticket_backend.venue.entity.Venue;
import com.project.event_ticket_backend.venue.mapper.VenueMapper;
import com.project.event_ticket_backend.venue.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;

    private final VenueMapper venueMapper;

    public List<VenueDto> getAllVenues() {
       List<Venue> venues = venueRepository.findAllByOrderByCreatedAtDesc();

       return venues.stream().map(venueMapper::toDto).toList();
    }
}

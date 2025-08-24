package com.project.event_ticket_backend.event.service;

import com.project.event_ticket_backend.event.dto.CreateEventDto;
import com.project.event_ticket_backend.event.dto.EventResponseDto;
import com.project.event_ticket_backend.event.dto.UpdateEventDto;
import com.project.event_ticket_backend.event.entity.Event;
import com.project.event_ticket_backend.event.mapper.EventMapper;
import com.project.event_ticket_backend.event.repository.EventRepository;
import com.project.event_ticket_backend.user.entity.User;
import com.project.event_ticket_backend.user.repository.UserRepository;
import com.project.event_ticket_backend.venue.repository.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final EventSeatService eventSeatService;

    public EventResponseDto createEvent(final CreateEventDto request) throws Exception{

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName(); // comes from UserDetails.getUsername()
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Logged-in user not found"));


//        var user = userRepository.findById(request.organizer_id())
//                .orElseThrow(() -> new Exception("User Not Found"));

        var venue = venueRepository.findById(request.venue_id())
                .orElseThrow(() -> new Exception("Venue Not found"));

        Instant endDate = Instant.parse(request.endDate());
        Instant startDate = Instant.parse(request.startDate());
        Instant ticketSaleStartDate = Instant.parse(request.ticketSaleStartDate());
        Instant ticketSaleEndDate = Instant.parse(request.ticketSaleEndDate());

        if (endDate.isBefore(startDate)) {
            throw new Exception("Invalid Date: End date cannot be before start date");
        }

        if (ticketSaleStartDate.isAfter(startDate)) {
            throw new Exception("Invalid Date: ticket sale should start before event start date");
        }

        if (ticketSaleEndDate.isAfter(endDate)){
            throw new Exception("Invalid Date: Ticket sale cannot continue after event ends.");
        }

        var event = eventMapper.toEventEntity(request, user, venue);
        eventRepository.save(event);

        eventSeatService.generateEventSeatFromVenue(event.getId());

        return eventMapper.toEventResponseDto(event);
    }

    public Page<EventResponseDto> getAllActiveEvents(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return eventRepository.findAll(pageable).map(eventMapper::toEventResponseDto);
    }

    public EventResponseDto updateEvent(UUID id, UpdateEventDto dto) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Event not found"));

        // Update fields
        event.setTitle(dto.title());
        event.setDescription(dto.description());
        event.setStartDate(Instant.parse(dto.startDate()));
        event.setEndDate(Instant.parse(dto.endDate()));
        event.setTicketSaleStartDate(Instant.parse(dto.ticketSaleStartDate()));
        event.setTicketSaleEndDate(Instant.parse(dto.ticketSaleEndDate()));

//        if (!event.getVenue().getId().equals(dto.venueId())) {
//            Venue venue = venueRepository.findById(dto.venueId())
//                    .orElseThrow(() -> new EntityNotFoundException("Venue not found"));
//            event.setVenue(venue);
//            // Delete the previous EventSeat objects from DB
//            eventSeatService.generateEventSeatFromVenue(event.getId());
//        }

        Event updated = eventRepository.save(event);
        return eventMapper.toEventResponseDto(updated);
    }

    public void deleteEvent(UUID id) {
        if (!eventRepository.existsById(id)) {
            throw new EntityNotFoundException("Event not found");
        }
        eventRepository.deleteById(id);
    }

}


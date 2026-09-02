package com.tickify.event.service;

import com.tickify.event.dto.CreateEventDto;
import com.tickify.event.dto.EventResponseDto;
import com.tickify.event.dto.UpdateEventDto;
import com.tickify.event.entity.Event;
import com.tickify.event.mapper.EventMapper;
import com.tickify.event.entity.TicketType;
import com.tickify.event.repository.EventRepository;
import com.tickify.event.repository.TicketTypeRepository;
import com.tickify.user.entity.User;
import com.tickify.user.repository.UserRepository;
import com.tickify.venue.repository.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final EventSeatService eventSeatService;
    private final TicketTypeRepository ticketTypeRepository;

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

    /**
     * A page of events, with their ticket tiers.
     *
     * <p>The tiers for the whole page are fetched in one query and grouped in memory. Mapping
     * each event independently issued a lookup per event, which a load test showed as an
     * events-list p95 several times its own median.
     */
    @Transactional(readOnly = true)
    public Page<EventResponseDto> getAllActiveEvents(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Event> events = eventRepository.findAll(pageable);

        List<UUID> eventIds = events.getContent().stream().map(Event::getId).toList();
        Map<UUID, List<TicketType>> tiersByEvent = eventIds.isEmpty()
                ? Map.of()
                : ticketTypeRepository.findByEvent_IdIn(eventIds).stream()
                        .collect(Collectors.groupingBy(tier -> tier.getEvent().getId()));

        return events.map(event ->
                eventMapper.toEventResponseDto(event, tiersByEvent.getOrDefault(event.getId(), List.of())));
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


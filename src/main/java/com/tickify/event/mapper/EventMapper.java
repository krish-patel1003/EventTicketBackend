package com.tickify.event.mapper;

import com.tickify.event.dto.CreateEventDto;
import com.tickify.event.dto.EventResponseDto;
import com.tickify.event.dto.TicketTypeDto;
import com.tickify.event.entity.Event;
import com.tickify.event.entity.TicketType;
import com.tickify.event.repository.TicketTypeRepository;
import com.tickify.user.entity.User;
import com.tickify.venue.entity.Venue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTypeMapper ticketTypeMapper;

    public Event toEventEntity(final CreateEventDto request, final User organizer, final Venue venue) {
        var event = new Event();
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setOrganizer(organizer);
        event.setVenue(venue);
        event.setStartDate(Instant.parse(request.startDate()));
        event.setEndDate(Instant.parse(request.endDate()));
        event.setTicketSaleStartDate(Instant.parse(request.ticketSaleStartDate()));
        event.setTicketSaleEndDate(Instant.parse(request.ticketSaleEndDate()));

        return event;
    }

    /** Single-event mapping; issues its own ticket-type lookup. */
    public EventResponseDto toEventResponseDto(final Event event) {
        return toEventResponseDto(event, ticketTypeRepository.findByEvent_Id(event.getId()));
    }

    /**
     * Mapping with the ticket types already in hand.
     *
     * <p>Used when rendering a page of events, so the caller can load every tier in one
     * query instead of one query per event.
     */
    public EventResponseDto toEventResponseDto(final Event event, final List<TicketType> ticketTypes) {
        List<TicketTypeDto> ticketTypeDtoList = ticketTypes.stream()
                .map(ticketTypeMapper::toTicketTypeDto)
                .toList();

        return new EventResponseDto(event.getId(),
                event.getTitle(), event.getDescription(), event.getOrganizer().getId(),
                event.getVenue().getId(), event.getStartDate().toString(), event.getEndDate().toString(),
                event.getTicketSaleStartDate().toString(), event.getTicketSaleEndDate().toString(),
                ticketTypeDtoList, event.isActive()
        );
    }
}

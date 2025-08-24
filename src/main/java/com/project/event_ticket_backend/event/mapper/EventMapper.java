package com.project.event_ticket_backend.event.mapper;

import com.project.event_ticket_backend.event.dto.CreateEventDto;
import com.project.event_ticket_backend.event.dto.EventResponseDto;
import com.project.event_ticket_backend.event.dto.TicketTypeDto;
import com.project.event_ticket_backend.event.entity.Event;
import com.project.event_ticket_backend.event.entity.EventSeat;
import com.project.event_ticket_backend.event.entity.TicketType;
import com.project.event_ticket_backend.event.repository.TicketTypeRepository;
import com.project.event_ticket_backend.user.entity.User;
import com.project.event_ticket_backend.venue.entity.Venue;
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

    public EventResponseDto toEventResponseDto(final Event event) {
        List<TicketType> ticketTypeList = ticketTypeRepository.findByEvent_Id(event.getId());

        List<TicketTypeDto> ticketTypeDtoList = ticketTypeList.stream()
                .map(ticketTypeMapper::toTicketTypeDto).toList();

        return new EventResponseDto(event.getId(),
                event.getTitle(), event.getDescription(), event.getOrganizer().getId(),
                event.getVenue().getId(), event.getStartDate().toString(), event.getEndDate().toString(),
                event.getTicketSaleStartDate().toString(), event.getTicketSaleEndDate().toString(),
                ticketTypeDtoList, event.isActive()
        );
    }
}

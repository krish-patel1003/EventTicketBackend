package com.project.event_ticket_backend.event.service;

import com.project.event_ticket_backend.event.dto.AssignTicketTypeDto;
import com.project.event_ticket_backend.event.dto.EventSeatDto;
import com.project.event_ticket_backend.event.entity.Event;
import com.project.event_ticket_backend.event.entity.EventSeat;
import com.project.event_ticket_backend.event.repository.EventRepository;
import com.project.event_ticket_backend.event.repository.EventSeatRepository;
import com.project.event_ticket_backend.venue.entity.VenueSeat;
import com.project.event_ticket_backend.venue.repository.VenueSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventSeatService {

    private final EventSeatRepository eventSeatRepository;
    private final VenueSeatRepository venueSeatRepository;
    private final EventRepository eventRepository;

    public void generateEventSeatFromVenue(UUID eventID) throws Exception {
        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new Exception("Event Not found"));

        if (event.getVenue() == null) {
            throw new Exception("Venue not set for the Event");
        }

        UUID venueId = event.getVenue().getId();

        List<VenueSeat> venueSeats = venueSeatRepository.findByVenueId(venueId);

        List<EventSeat> eventSeats = new ArrayList<>(venueSeats.size());

        for (VenueSeat vs: venueSeats) {
            eventSeats.add(createEventSeatFromVenueSeat(vs, event));
        }

        eventSeatRepository.saveAll(eventSeats);
    }

    private EventSeat createEventSeatFromVenueSeat(VenueSeat vs, Event event) {
        EventSeat es = new EventSeat();
        es.setEvent(event);
        es.setSeatNumber(vs.getSeatNumber());
        es.setSection(vs.getSection());
        es.setRowLabel(vs.getRowLabel());
        es.setLocked(false);
        es.setReserved(false);
        return es;
    }

    @Transactional
    public List<EventSeatDto> getAvailableSeats(UUID eventId) {
        List<EventSeat> seats = eventSeatRepository.findByEvent_IdAndIsLockedFalseAndIsReservedFalse(eventId);

        return seats.stream().map(es -> new EventSeatDto(
                    es.getEvent().getId(),
                    es.getEvent().getTitle(),
                    es.getSeatNumber(),
                    es.getTicketType() != null ? es.getTicketType().getTitle() : null,
                    es.getRowLabel(),
                    es.getSection(),
                    es.isLocked(),
                    es.isReserved())
        ).toList();
    }

    @Transactional
    public void assignTicketType(AssignTicketTypeDto request) throws Exception{

        int updated = 0;

        UUID eventId = request.eventId();
        UUID ticketTypeId = request.ticketTypeId();

        if(request.seatId() != null) {
            updated = eventSeatRepository.assignTicketTypeBySeatId(eventId, ticketTypeId, request.seatId());
        }
        else if (request.rowLabel() != null) {
            updated = eventSeatRepository.assignTicketTypeByRowLabel(eventId, ticketTypeId, request.rowLabel());
        }
        else if (request.section() != null) {
            updated = eventSeatRepository.assignTicketTypeBySection(eventId, ticketTypeId, request.section());
        }
        else {
            updated = eventSeatRepository.assignTicketTypeToAll(eventId, ticketTypeId);
        }

        if (updated == 0) {
            throw new Exception("No seats updated. Check eventId/section/row/seat parameters.");
        }
    }
}

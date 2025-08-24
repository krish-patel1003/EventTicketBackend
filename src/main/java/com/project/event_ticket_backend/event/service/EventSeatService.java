package com.project.event_ticket_backend.event.service;

import com.project.event_ticket_backend.event.entity.Event;
import com.project.event_ticket_backend.event.entity.EventSeat;
import com.project.event_ticket_backend.event.repository.EventRepository;
import com.project.event_ticket_backend.event.repository.EventSeatRepository;
import com.project.event_ticket_backend.venue.entity.VenueSeat;
import com.project.event_ticket_backend.venue.repository.VenueSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}

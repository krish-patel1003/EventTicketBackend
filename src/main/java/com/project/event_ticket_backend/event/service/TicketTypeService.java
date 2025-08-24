package com.project.event_ticket_backend.event.service;

import com.project.event_ticket_backend.event.dto.CreateTicketTypeDto;
import com.project.event_ticket_backend.event.dto.TicketTypeDto;
import com.project.event_ticket_backend.event.dto.UpdateTicketTypeDto;
import com.project.event_ticket_backend.event.entity.TicketType;
import com.project.event_ticket_backend.event.mapper.TicketTypeMapper;
import com.project.event_ticket_backend.event.repository.EventRepository;
import com.project.event_ticket_backend.event.repository.TicketTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketTypeService {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final TicketTypeMapper ticketTypeMapper;

    public TicketTypeDto createTicketType(final CreateTicketTypeDto request) throws Exception {
        final var event = eventRepository.findById(request.event_id())
                .orElseThrow(() -> new Exception("Event with id [%s] not found.".formatted(request.event_id())));

        final TicketType ticketType = ticketTypeMapper.toTicketTypeEntity(request, event);

        ticketTypeRepository.save(ticketType);

        return ticketTypeMapper.toTicketTypeDto(ticketType);
    }

    public List<TicketTypeDto> getTicketTypesByEvent(UUID eventId) throws Exception{
        if(!eventRepository.existsById(eventId)){
            throw new Exception("Event with id [%s] not found.".formatted(eventId));
        }

        return ticketTypeRepository.findByEvent_Id(eventId)
                .stream().map(ticketTypeMapper::toTicketTypeDto).toList();

    }

    public TicketTypeDto updateTicketType(UUID id, UpdateTicketTypeDto dto) {
        TicketType ticketType = ticketTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Ticket type not found"));

        ticketType.setTitle(dto.title());
        ticketType.setDescription(dto.description());
        ticketType.setPrice(dto.price());
        ticketType.setTotalQuantity(dto.totalQuantity());


        TicketType updated = ticketTypeRepository.save(ticketType);
        return ticketTypeMapper.toTicketTypeDto(updated);
    }

    public void deleteTicketType(UUID id) {
        if (!ticketTypeRepository.existsById(id)) {
            throw new EntityNotFoundException("Ticket type not found");
        }
        ticketTypeRepository.deleteById(id);
    }

}

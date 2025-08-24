package com.project.event_ticket_backend.event.mapper;

import com.project.event_ticket_backend.event.dto.CreateTicketTypeDto;
import com.project.event_ticket_backend.event.dto.TicketTypeDto;
import com.project.event_ticket_backend.event.entity.Event;
import com.project.event_ticket_backend.event.entity.TicketType;
import org.springframework.stereotype.Component;

@Component
public class TicketTypeMapper {

    public TicketType toTicketTypeEntity(final CreateTicketTypeDto dto, final Event event) {
        var ticketType = new TicketType();
        ticketType.setTitle(dto.title());
        ticketType.setDescription(dto.description());
        ticketType.setPrice(dto.price());
        ticketType.setEvent(event);
        ticketType.setTotalQuantity(dto.totalQuantity());
        ticketType.setAvailableQuantity(dto.totalQuantity());
        return ticketType;
    }

    public TicketTypeDto toTicketTypeDto(final TicketType ticketType) {
        return new TicketTypeDto(
                ticketType.getId(),
                ticketType.getTitle(),
                ticketType.getDescription(),
                ticketType.getPrice(),
                ticketType.getTotalQuantity(),
                ticketType.getAvailableQuantity()
        );
    }
}

package com.project.event_ticket_backend.event.controller;

import com.project.event_ticket_backend.event.dto.CreateTicketTypeDto;
import com.project.event_ticket_backend.event.dto.TicketTypeDto;
import com.project.event_ticket_backend.event.dto.UpdateTicketTypeDto;
import com.project.event_ticket_backend.event.service.TicketTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/ticket-types")
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    @PostMapping("/")
    public ResponseEntity<TicketTypeDto> createTicketType(@RequestBody CreateTicketTypeDto request) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketTypeService.createTicketType(request));
    }

    @PreAuthorize("hasRole('USER') or hasRole('ORGANIZER') or hasRole('ADMIN')")
    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<TicketTypeDto>> getByEventId(@PathVariable UUID eventId) throws Exception {
        return ResponseEntity.ok(ticketTypeService.getTicketTypesByEvent(eventId));
    }

    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<TicketTypeDto> updateTicketType(
            @PathVariable UUID id,
            @RequestBody UpdateTicketTypeDto request) {
        TicketTypeDto updated = ticketTypeService.updateTicketType(id, request);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicketType(@PathVariable UUID id) {
        ticketTypeService.deleteTicketType(id);
        return ResponseEntity.noContent().build();
    }

}

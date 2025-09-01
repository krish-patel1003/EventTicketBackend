package com.project.event_ticket_backend.event.controller;

import com.project.event_ticket_backend.event.dto.AssignTicketTypeDto;
import com.project.event_ticket_backend.event.service.EventSeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/event-seats")
@RequiredArgsConstructor
public class EventSeatController {

    private final EventSeatService eventSeatService;

    @PostMapping("/assign-ticket-type")
    public ResponseEntity<?> assignTicketTypesToSeats(@RequestBody AssignTicketTypeDto request) throws Exception {
        eventSeatService.assignTicketType(request);
        return ResponseEntity.ok(Map.of("Updated", true));
    }
}

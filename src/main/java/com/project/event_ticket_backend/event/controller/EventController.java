package com.project.event_ticket_backend.event.controller;

import com.project.event_ticket_backend.event.dto.CreateEventDto;
import com.project.event_ticket_backend.event.dto.EventResponseDto;
import com.project.event_ticket_backend.event.dto.UpdateEventDto;
import com.project.event_ticket_backend.event.mapper.EventMapper;
import com.project.event_ticket_backend.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventMapper eventMapper;
    private final EventService eventService;

    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    @PostMapping("/")
    public ResponseEntity<EventResponseDto> createEvent(@RequestBody CreateEventDto request) throws Exception {
        EventResponseDto response = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('USER') or hasRole('ORGANIZER') or hasRole('ADMIN')")
    @GetMapping("/")
    public ResponseEntity<Page<EventResponseDto>> getAllActiveEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        return ResponseEntity.ok(eventService.getAllActiveEvents(page, size));
    }

    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<EventResponseDto> updateEvent(
            @PathVariable UUID id,
            @RequestBody UpdateEventDto request) {
        EventResponseDto updated = eventService.updateEvent(id, request);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }
}

package com.project.event_ticket_backend.booking.controller;

import com.project.event_ticket_backend.booking.dto.QueueDto;
import com.project.event_ticket_backend.booking.service.QueueService;
import com.project.event_ticket_backend.user.service.JpaUserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("api/v1/booking/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/{eventId}/join")
    public ResponseEntity<QueueDto> join(@PathVariable("eventId") UUID eventId,
                                         @AuthenticationPrincipal JpaUserDetailsImpl loggedInUser) {
        long pos = queueService.join(eventId, loggedInUser.getUser().getId());
        QueueDto response = new QueueDto(eventId, loggedInUser.getUser().getId(), pos, false);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{eventId}/status")
    public ResponseEntity<QueueDto> status(@PathVariable("eventId") UUID eventId,
                                           @AuthenticationPrincipal JpaUserDetailsImpl loggedInUser) {

        UUID loggedIndUserId = loggedInUser.getUser().getId();
        long pos = queueService.position(eventId, loggedIndUserId);
        boolean active = queueService.hasActiveSlot(eventId, loggedIndUserId);
        QueueDto response = new QueueDto(eventId, loggedIndUserId, pos, active);
        return ResponseEntity.ok(response);
    }

}

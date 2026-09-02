package com.tickify.booking.controller;

import com.tickify.booking.dto.QueueDto;
import com.tickify.booking.service.QueueService;
import com.tickify.user.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking/queue")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Tag(name = "Waiting room", description = "Virtual queue that throttles admission to the seat map")
public class QueueController {

    private final QueueService queueService;
    private final CurrentUserService currentUser;

    @Operation(summary = "Join the waiting room for an event",
            description = "Idempotent — re-joining keeps your original position.")
    @PostMapping("/{eventId}/join")
    public ResponseEntity<QueueDto> join(@PathVariable("eventId") UUID eventId) {
        UUID userId = currentUser.requireId();
        long position = queueService.join(eventId, userId);

        return ResponseEntity.ok(new QueueDto(eventId, userId, position,
                queueService.hasActiveSlot(eventId, userId)));
    }

    @Operation(summary = "Poll your queue position",
            description = "`active: true` means you have been admitted and may lock seats.")
    @GetMapping("/{eventId}/status")
    public ResponseEntity<QueueDto> status(@PathVariable("eventId") UUID eventId) {
        UUID userId = currentUser.requireId();

        return ResponseEntity.ok(new QueueDto(eventId, userId,
                queueService.position(eventId, userId),
                queueService.hasActiveSlot(eventId, userId)));
    }
}

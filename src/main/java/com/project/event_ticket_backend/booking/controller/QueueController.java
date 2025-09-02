package com.project.event_ticket_backend.booking.controller;

import com.project.event_ticket_backend.booking.dto.QueueDto;
import com.project.event_ticket_backend.booking.service.QueueService;
import com.project.event_ticket_backend.user.entity.User;
import com.project.event_ticket_backend.user.repository.UserRepository;
import com.project.event_ticket_backend.user.service.JpaUserDetailsImpl;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("api/v1/booking/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;
    private final UserRepository userRepository;

    private User getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new EntityNotFoundException("No authenticated user found");
        }

        String email = authentication.getName(); // from UserDetails.getUsername()
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Logged-in user not found"));
    }

    @PostMapping("/{eventId}/join")
    public ResponseEntity<QueueDto> join(@PathVariable("eventId") UUID eventId) {
        User user = getLoggedInUser();

        long pos = queueService.join(eventId, user.getId());
        QueueDto response = new QueueDto(eventId, user.getId(), pos, false);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{eventId}/status")
    public ResponseEntity<QueueDto> status(@PathVariable("eventId") UUID eventId) {
        User user = getLoggedInUser();

        long pos = queueService.position(eventId, user.getId());
        boolean active = queueService.hasActiveSlot(eventId, user.getId());

        QueueDto response = new QueueDto(eventId, user.getId(), pos, active);

        return ResponseEntity.ok(response);
    }
}

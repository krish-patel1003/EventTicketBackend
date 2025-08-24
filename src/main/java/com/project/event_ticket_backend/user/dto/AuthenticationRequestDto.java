package com.project.event_ticket_backend.user.dto;

public record AuthenticationRequestDto(
        String email,
        String password
) {
}

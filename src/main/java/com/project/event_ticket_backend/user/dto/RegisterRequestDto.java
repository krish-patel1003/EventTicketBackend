package com.project.event_ticket_backend.user.dto;

import java.util.Set;

public record RegisterRequestDto(
        String email,
        String password,
        Set<String> requestedRoles
) {
}

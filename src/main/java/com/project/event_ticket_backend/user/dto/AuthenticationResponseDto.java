package com.project.event_ticket_backend.user.dto;

import java.util.UUID;

public record AuthenticationResponseDto(String accessToken, UUID refreshToken) {
}

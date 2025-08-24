package com.project.event_ticket_backend.dto;

import java.time.Instant;

public record ErrorResponseDto (
        String timestamp,
        int status,
        String error,
        String message,
        String path
){
}

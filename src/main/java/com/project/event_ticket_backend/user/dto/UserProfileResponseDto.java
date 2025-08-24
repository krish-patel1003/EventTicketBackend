package com.project.event_ticket_backend.user.dto;

import java.util.Set;

public record UserProfileResponseDto (
        String email,
        Set<String> roles,
        boolean emailVerified
){
}

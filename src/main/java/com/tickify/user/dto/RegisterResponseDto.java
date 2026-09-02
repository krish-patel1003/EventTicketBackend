package com.tickify.user.dto;

import java.util.Set;
import java.util.UUID;

public record RegisterResponseDto(
        UUID id,
        String email,
        boolean emailVerifiedRequired,
        Set<String> roles
) {
}

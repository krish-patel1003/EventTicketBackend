package com.tickify.user.dto;

import java.util.Set;

public record RegisterRequestDto(
        String email,
        String password,
        Set<String> requestedRoles
) {
}

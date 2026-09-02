package com.tickify.user.dto;

public record AuthenticationRequestDto(
        String email,
        String password
) {
}

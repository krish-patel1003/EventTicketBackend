package com.tickify.user.dto;

import java.util.Set;

public record UserProfileResponseDto (
        String email,
        Set<String> roles,
        boolean emailVerified
){
}

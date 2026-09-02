package com.tickify.dto;

import java.time.Instant;

public record ErrorResponseDto (
        String timestamp,
        int status,
        String error,
        String message,
        String path
){
}

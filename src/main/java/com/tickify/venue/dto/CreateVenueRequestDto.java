package com.tickify.venue.dto;

public record CreateVenueRequestDto(
        String name,
        String location,
        int totalRows,
        int seatsPerRow,
        String rowPrefix
) {
}

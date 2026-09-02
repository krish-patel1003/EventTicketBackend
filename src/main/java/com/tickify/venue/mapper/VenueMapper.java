package com.tickify.venue.mapper;

import com.tickify.venue.dto.VenueDto;
import com.tickify.venue.entity.Venue;
import org.springframework.stereotype.Component;

@Component
public class VenueMapper {

    /**
     * @param seatCount counted by the caller in one grouped query, rather than walking
     *                  {@code venue.getSeats()} — that association is lazy and holds
     *                  every seat in the venue, which is tens of thousands of rows.
     */
    public VenueDto toDto(Venue venue, long seatCount) {
        if (venue == null) {
            return null;
        }

        return new VenueDto(
                venue.getId(),
                venue.getName(),
                venue.getLocation(),
                seatCount,
                venue.getCreatedAt()
        );
    }
}

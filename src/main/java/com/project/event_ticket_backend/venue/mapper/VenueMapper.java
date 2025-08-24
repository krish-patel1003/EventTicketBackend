package com.project.event_ticket_backend.venue.mapper;

import com.project.event_ticket_backend.venue.dto.VenueDto;
import com.project.event_ticket_backend.venue.entity.Venue;
import org.springframework.stereotype.Component;

@Component
public class VenueMapper {

    public VenueDto toDto(Venue venue) {
        if (venue == null) {
            return null;
        }

        return new VenueDto(
                venue.getId(),
                venue.getName(),
                venue.getLocation(),
                venue.getCreatedAt()
        );
    }

    public Venue toEntity(VenueDto dto) {
        if (dto == null) {
            return null;
        }

        Venue venue = new Venue();
        venue.setId(dto.id()); // careful: usually you don’t set ID manually for new entities
        venue.setName(dto.name());
        venue.setLocation(dto.location());
        venue.setCreatedAt(dto.createdAt()); // BaseEntity should normally set this automatically

        return venue;
    }
}

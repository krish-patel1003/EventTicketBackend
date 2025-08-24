package com.project.event_ticket_backend.venue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.event_ticket_backend.config.BaseEntity;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "venue_seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"venue_id", "seat_number"}))
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, exclude = "venue")
public class VenueSeat extends BaseEntity {

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column
    private String section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    @JsonIgnore
    private Venue venue;
}

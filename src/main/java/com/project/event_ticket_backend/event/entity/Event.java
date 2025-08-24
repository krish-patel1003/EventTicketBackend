package com.project.event_ticket_backend.event.entity;

import com.project.event_ticket_backend.config.BaseEntity;
import com.project.event_ticket_backend.user.entity.User;
import com.project.event_ticket_backend.venue.entity.Venue;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "events")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class Event extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false, updatable = false)
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(name = "ticket_sale_start_date", nullable = false)
    private Instant ticketSaleStartDate;

    @Column(name = "ticket_sale_end_date", nullable = false)
    private Instant ticketSaleEndDate;

    @Column(name = "is_active")
    private boolean isActive = true;
}

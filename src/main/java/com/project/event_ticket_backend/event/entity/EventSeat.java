package com.project.event_ticket_backend.event.entity;

import com.project.event_ticket_backend.config.BaseEntity;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "event_seats",
uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_number"}))
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class EventSeat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(nullable = false)
    private String section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id")
    private TicketType ticketType;

    @Column(name = "is_locked")
    private boolean isLocked;

    @Column(name = "is_reserved")
    private boolean isReserved;
}

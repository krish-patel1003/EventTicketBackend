package com.project.event_ticket_backend.booking.entity;

import com.fasterxml.jackson.databind.ser.Serializers;
import com.project.event_ticket_backend.config.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "qr_codes")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class QRCode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Column(name = "qr_code", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "is_valid", nullable = false)
    private boolean isValid = true;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private EntryMethodType method = EntryMethodType.SCAN;
}


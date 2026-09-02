package com.tickify.booking.entity;

import com.tickify.config.BaseEntity;
import com.tickify.event.entity.EventSeat;
import jakarta.persistence.*;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "booking_seats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookingSeat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private EventSeat seat;

    /**
     * When this claim on the seat was given up — a declined payment, or an abandoned
     * checkout swept away by {@code AbandonedBookingReaper}. Null means the claim is live.
     *
     * <p>A partial unique index on {@code (seat_id) WHERE released_at IS NULL} is what
     * actually prevents two live claims on the same seat, as a backstop behind the Redis lock.
     */
    @Column(name = "released_at")
    private Instant releasedAt;

    public boolean isReleased() {
        return releasedAt != null;
    }

    public void release(Instant at) {
        this.releasedAt = at;
    }
}


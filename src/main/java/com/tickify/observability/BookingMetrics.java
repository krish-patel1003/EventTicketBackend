package com.tickify.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Domain counters for the booking funnel, exported on {@code /actuator/prometheus}.
 *
 * <p>Request-level latency comes free from Spring's {@code http.server.requests} timer.
 * What that does not tell you is <em>why</em> a spike happened, so these meters track the
 * two things that actually decide whether a ticket drop holds up:
 * seat-lock contention and the payment saga's outcome.
 */
@Component
public class BookingMetrics {

    private final Counter seatLockAcquired;
    private final Counter seatLockContended;
    private final Counter queueJoined;
    private final Counter queuePromoted;
    private final Counter bookingCreated;
    private final Counter bookingConfirmed;
    private final Counter bookingFailed;
    private final Timer seatLockTimer;

    public BookingMetrics(MeterRegistry registry) {
        this.seatLockAcquired = Counter.builder("tickify.seat.lock")
                .tag("result", "acquired")
                .description("Seat locks successfully taken in Redis")
                .register(registry);

        this.seatLockContended = Counter.builder("tickify.seat.lock")
                .tag("result", "contended")
                .description("Seat locks refused because another user held the seat")
                .register(registry);

        this.queueJoined = Counter.builder("tickify.queue.joined")
                .description("Users who entered the virtual waiting room")
                .register(registry);

        this.queuePromoted = Counter.builder("tickify.queue.promoted")
                .description("Users promoted from the waiting room into the booking window")
                .register(registry);

        this.bookingCreated = Counter.builder("tickify.booking")
                .tag("state", "pending")
                .description("Pending bookings created after a successful seat lock")
                .register(registry);

        this.bookingConfirmed = Counter.builder("tickify.booking")
                .tag("state", "confirmed")
                .description("Bookings confirmed after a successful payment")
                .register(registry);

        this.bookingFailed = Counter.builder("tickify.booking")
                .tag("state", "failed")
                .description("Bookings released after a failed payment")
                .register(registry);

        this.seatLockTimer = Timer.builder("tickify.seat.lock.duration")
                .description("Time spent acquiring the Redis seat lock")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void seatLockAcquired() {
        seatLockAcquired.increment();
    }

    public void seatLockContended() {
        seatLockContended.increment();
    }

    public void queueJoined() {
        queueJoined.increment();
    }

    public void queuePromoted(int count) {
        queuePromoted.increment(count);
    }

    public void bookingCreated() {
        bookingCreated.increment();
    }

    public void bookingConfirmed() {
        bookingConfirmed.increment();
    }

    public void bookingFailed() {
        bookingFailed.increment();
    }

    public Timer seatLockTimer() {
        return seatLockTimer;
    }
}

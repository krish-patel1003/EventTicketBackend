package com.tickify.booking.service;

import com.tickify.config.properties.TickifyProperties;
import com.tickify.observability.BookingMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Distributed seat locks, held in Redis.
 *
 * <p>A lock is a {@code SET key value NX PX ttl}: the first request to reach Redis wins the
 * seat and everyone else is refused. Because Redis executes commands one at a time, this is
 * the single point that makes concurrent seat selection safe — the database is never asked
 * to arbitrate, so a ticket drop does not turn into row-lock contention on {@code event_seats}.
 *
 * <p>The TTL is what makes abandoned checkouts self-healing: a user who closes the tab after
 * selecting seats releases them automatically, with no reaper job to run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeatLockService {

    private final StringRedisTemplate redis;
    private final TickifyProperties properties;
    private final BookingMetrics metrics;

    /**
     * Release is compare-and-delete, not a plain DEL: a request must never be able to drop
     * a lock that has already expired and been re-acquired by a different user.
     */
    private static final DefaultRedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private String lockKey(UUID eventId, UUID seatId) {
        return "seatLock:event:" + eventId + ":seat:" + seatId;
    }

    private Duration ttl() {
        return properties.getSeatLock().getTtl();
    }

    public boolean tryLockSeat(UUID eventId, UUID seatId, UUID userId) {
        long start = System.nanoTime();
        try {
            String key = lockKey(eventId, seatId);
            boolean acquired = Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(key, userId.toString(), ttl()));

            if (acquired) {
                metrics.seatLockAcquired();
            } else {
                metrics.seatLockContended();
                log.debug("Seat {} for event {} already locked; refusing user {}", seatId, eventId, userId);
            }
            return acquired;
        } finally {
            metrics.seatLockTimer().record(Duration.ofNanos(System.nanoTime() - start));
        }
    }

    /**
     * Unconditional release. Used by the payment saga, which owns the booking and therefore
     * the seat regardless of which request originally locked it.
     */
    public void releaseSeat(UUID eventId, UUID seatId) {
        redis.delete(lockKey(eventId, seatId));
    }

    /** Release only if {@code userId} still holds the lock. */
    public void releaseSeat(UUID eventId, UUID seatId, UUID userId) {
        redis.execute(RELEASE_IF_OWNER, List.of(lockKey(eventId, seatId)), userId.toString());
    }

    public boolean isLockedBy(UUID eventId, UUID seatId, UUID userId) {
        String v = redis.opsForValue().get(lockKey(eventId, seatId));
        return userId.toString().equals(v);
    }
}

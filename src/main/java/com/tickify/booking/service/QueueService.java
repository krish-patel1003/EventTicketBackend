package com.tickify.booking.service;

import com.tickify.config.properties.TickifyProperties;
import com.tickify.observability.BookingMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The virtual waiting room.
 *
 * <p>When 50,000 people hit "buy" at 10:00:00, admitting all of them to the seat map is what
 * takes a ticketing site down. Instead every arrival is parked in a Redis sorted set scored by
 * arrival time (FIFO), and a background promoter admits a fixed batch per second. The load the
 * booking path sees is therefore bounded by the promotion rate rather than by demand.
 *
 * <p>Redis keys per event:
 * <pre>
 *   queue:event:{id}                ZSET  waiting users, score = enqueue millis
 *   queue:event:{id}:active         SET   users currently inside the booking window
 *   queue:event:{id}:slot:{userId}  STR   the admission token itself, with a TTL
 * </pre>
 * The slot key's TTL is the mechanism: when it expires the user drops out of the booking
 * window without any bookkeeping, which keeps the window self-limiting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final StringRedisTemplate redis;
    private final TickifyProperties properties;
    private final BookingMetrics metrics;

    private static final String ACTIVE_EVENTS_KEY = "queue:activeEvents";

    private String zsetKey(UUID eventId) { return "queue:event:" + eventId; }
    private String activeKey(UUID eventId) { return "queue:event:" + eventId + ":active"; }
    private String slotKey(UUID eventId, UUID userId) { return "queue:event:" + eventId + ":slot:" + userId; }

    /** 1-based position in the waiting room, or -1 if the user is not queued. */
    public long position(UUID eventId, UUID userId) {
        Long rank = redis.opsForZSet().rank(zsetKey(eventId), userId.toString());
        return rank == null ? -1 : rank + 1;
    }

    /** Idempotent: re-joining an event you are already queued for keeps your original place. */
    public long join(UUID eventId, UUID userId) {
        Double added = redis.opsForZSet().score(zsetKey(eventId), userId.toString());

        if (added != null) return position(eventId, userId);

        redis.opsForZSet().add(zsetKey(eventId), userId.toString(), System.currentTimeMillis());
        // Track which events have a live queue so the promoter knows what to work on.
        redis.opsForSet().add(ACTIVE_EVENTS_KEY, eventId.toString());
        metrics.queueJoined();

        return position(eventId, userId);
    }

    /** Called by {@code QueuePromoter}: move the front {@code batchSize} users into the booking window. */
    public List<UUID> promote(UUID eventId, int batchSize) {
        Set<String> batch = redis.opsForZSet().range(zsetKey(eventId), 0, batchSize - 1);

        if (batch == null || batch.isEmpty()) {
            cleanupIfEmpty(eventId);
            return List.of();
        }

        List<UUID> promoted = new ArrayList<>(batch.size());

        for (String uid : batch) {
            redis.opsForSet().add(activeKey(eventId), uid);
            redis.opsForValue().set(slotKey(eventId, UUID.fromString(uid)), "granted",
                    properties.getQueue().getSlotTtl());
            redis.opsForZSet().remove(zsetKey(eventId), uid);
            promoted.add(UUID.fromString(uid));
        }

        metrics.queuePromoted(promoted.size());
        return promoted;
    }

    public boolean hasActiveSlot(UUID eventId, UUID userId) {
        return Boolean.TRUE.equals(redis.hasKey(slotKey(eventId, userId)));
    }

    public void releaseSlot(UUID eventId, UUID userId) {
        redis.delete(slotKey(eventId, userId));
        redis.opsForSet().remove(activeKey(eventId), userId.toString());
        cleanupIfEmpty(eventId);
    }

    /** Stop the promoter from polling an event whose queue has fully drained. */
    private void cleanupIfEmpty(UUID eventId) {
        Long queueSize = redis.opsForZSet().size(zsetKey(eventId));
        Long activeSize = redis.opsForSet().size(activeKey(eventId));

        if ((queueSize == null || queueSize == 0) && (activeSize == null || activeSize == 0)) {
            redis.opsForSet().remove(ACTIVE_EVENTS_KEY, eventId.toString());
        }
    }

    public Set<String> getActiveEvents() {
        return redis.opsForSet().members(ACTIVE_EVENTS_KEY);
    }

    /** Number of users still waiting for this event. */
    public long waitingCount(UUID eventId) {
        Long size = redis.opsForZSet().size(zsetKey(eventId));
        return size == null ? 0 : size;
    }
}

package com.project.event_ticket_backend.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final StringRedisTemplate redis;
    private static final Duration SLOT_TTL = Duration.ofMinutes(7);

    private static final String ACTIVE_EVENTS_KEY = "queue:activeEvents";

    private String zsetKey(UUID eventId) { return "queue:event:" + eventId; }
    private String activeKey(UUID eventId) { return "queue:event:" + eventId + ":active"; }
    private String slotKey(UUID eventId, UUID userId) { return "queue:event:" + eventId + ":slot:" + userId; }

    public long position(UUID eventId, UUID userId) {
        Long rank = redis.opsForZSet().rank(zsetKey(eventId), userId.toString());
        return rank == null ? -1 : rank + 1;
    }

    // FIFO via ZSET score = enqueue time (millis)
    public long join(UUID eventId, UUID userId) {
        Double added = redis.opsForZSet().score(zsetKey(eventId), userId.toString());

        if (added != null) return position(eventId, userId);

        redis.opsForZSet().add(zsetKey(eventId), userId.toString(), System.currentTimeMillis());
        // Add eventId to activeEvents set
        redis.opsForSet().add(ACTIVE_EVENTS_KEY, eventId.toString());

        return position(eventId, userId);
    }

    // Worker calls this to promote N users from queue to active
    @Transactional
    public List<UUID> promote(UUID eventId, int batchSize) {
        Set<String> batch = redis.opsForZSet().range(zsetKey(eventId), 0, batchSize - 1);

        if (batch == null || batch.isEmpty()) {
            cleanupIfEmpty(eventId);
            return List.of();
        }

        List<UUID> promoted = new ArrayList<>();

        for (String uid : batch) {
            // add to active set & issue "slot token" (a Redis key with TTL)
            redis.opsForSet().add(activeKey(eventId), uid);
            redis.opsForValue().set(slotKey(eventId, UUID.fromString(uid)), "granted", SLOT_TTL);
            redis.opsForZSet().remove(zsetKey(eventId), uid);
            promoted.add(UUID.fromString(uid));
        }

        return promoted;
    }

    public boolean hasActiveSlot(UUID eventId, UUID userId) {
        return redis.hasKey(slotKey(eventId, userId));
    }

    public void releaseSlot(UUID eventId, UUID userId) {
        redis.delete(slotKey(eventId, userId));
        redis.opsForSet().remove(activeKey(eventId), userId.toString());
        cleanupIfEmpty(eventId);
    }

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
}

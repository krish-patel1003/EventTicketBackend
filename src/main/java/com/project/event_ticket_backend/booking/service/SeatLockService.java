package com.project.event_ticket_backend.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatLockService {

    private final StringRedisTemplate redis;

    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private String lockKey(UUID eventId, UUID seatId) {
        return "seatLock:event:" + eventId + ":seat:" + seatId;
    }

    public boolean tryLockSeat(UUID eventId, UUID seatId, UUID userId) {
        String key = lockKey(eventId, seatId);
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, userId.toString(), LOCK_TTL));
    }

    public void releaseSeat(UUID eventId, UUID seatId) {
        redis.delete(lockKey(eventId, seatId));
    }

    public boolean isLockedBy(UUID eventId, UUID seatId, UUID userId) {
        String v = redis.opsForValue().get(lockKey(eventId, seatId));
        return userId.toString().equals(v);
    }
}

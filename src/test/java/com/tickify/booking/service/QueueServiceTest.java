package com.tickify.booking.service;

import com.tickify.config.properties.TickifyProperties;
import com.tickify.observability.BookingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueService")
class QueueServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ZSetOperations<String, String> zSetOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private ValueOperations<String, String> valueOps;

    private QueueService queueService;
    private TickifyProperties properties;

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForZSet()).thenReturn(zSetOps);
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);

        properties = new TickifyProperties();
        properties.getQueue().setSlotTtl(Duration.ofMinutes(7));

        queueService = new QueueService(redis, properties, new BookingMetrics(new SimpleMeterRegistry()));
    }

    private String zsetKey() {
        return "queue:event:" + eventId;
    }

    @Test
    @DisplayName("joining enqueues the user scored by arrival time and returns a 1-based position")
    void joinEnqueuesUser() {
        when(zSetOps.score(zsetKey(), userId.toString())).thenReturn(null);
        when(zSetOps.rank(zsetKey(), userId.toString())).thenReturn(0L);

        assertThat(queueService.join(eventId, userId)).isEqualTo(1L);

        verify(zSetOps).add(eq(zsetKey()), eq(userId.toString()), anyDouble());
        verify(setOps).add("queue:activeEvents", eventId.toString());
    }

    @Test
    @DisplayName("re-joining is idempotent and keeps the original position")
    void joinIsIdempotent() {
        // Already queued in 42nd place.
        when(zSetOps.score(zsetKey(), userId.toString())).thenReturn(1_700_000_000_000d);
        when(zSetOps.rank(zsetKey(), userId.toString())).thenReturn(41L);

        assertThat(queueService.join(eventId, userId)).isEqualTo(42L);

        // Re-adding would reset the score and send the user to the back of the queue.
        verify(zSetOps, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("position is -1 for a user who is not queued")
    void positionIsMinusOneWhenNotQueued() {
        when(zSetOps.rank(zsetKey(), userId.toString())).thenReturn(null);

        assertThat(queueService.position(eventId, userId)).isEqualTo(-1L);
    }

    @Test
    @DisplayName("promotion admits the front of the queue, issues a TTL slot and dequeues them")
    void promoteMovesFrontOfQueueIntoBookingWindow() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Set<String> batch = new LinkedHashSet<>(List.of(first.toString(), second.toString()));

        when(zSetOps.range(zsetKey(), 0, 1)).thenReturn(batch);

        assertThat(queueService.promote(eventId, 2)).containsExactly(first, second);

        for (UUID uid : List.of(first, second)) {
            verify(setOps).add("queue:event:" + eventId + ":active", uid.toString());
            verify(valueOps).set("queue:event:" + eventId + ":slot:" + uid, "granted", Duration.ofMinutes(7));
            verify(zSetOps).remove(zsetKey(), uid.toString());
        }
    }

    @Test
    @DisplayName("promotion of an empty queue stops the promoter polling that event")
    void promoteCleansUpDrainedEvent() {
        when(zSetOps.range(zsetKey(), 0, 49)).thenReturn(Set.of());
        when(zSetOps.size(zsetKey())).thenReturn(0L);
        when(setOps.size("queue:event:" + eventId + ":active")).thenReturn(0L);

        assertThat(queueService.promote(eventId, 50)).isEmpty();

        verify(setOps).remove("queue:activeEvents", eventId.toString());
    }

    @Test
    @DisplayName("an event with users still inside the booking window stays registered")
    void doesNotCleanUpWhileUsersAreStillActive() {
        when(zSetOps.range(zsetKey(), 0, 49)).thenReturn(Set.of());
        when(zSetOps.size(zsetKey())).thenReturn(0L);
        when(setOps.size("queue:event:" + eventId + ":active")).thenReturn(3L);

        queueService.promote(eventId, 50);

        verify(setOps, never()).remove(anyString(), anyString());
    }

    @Test
    @DisplayName("an active slot exists only while the TTL key is present")
    void activeSlotFollowsTheTtlKey() {
        String slotKey = "queue:event:" + eventId + ":slot:" + userId;

        when(redis.hasKey(slotKey)).thenReturn(true, false);

        assertThat(queueService.hasActiveSlot(eventId, userId)).isTrue();
        assertThat(queueService.hasActiveSlot(eventId, userId)).isFalse();
    }

    @Test
    @DisplayName("promotion batch size comes from configuration")
    void promotionBatchSizeIsConfigurable() {
        properties.getQueue().setPromotionBatchSize(500);
        assertThat(properties.getQueue().getPromotionBatchSize()).isEqualTo(500);
    }
}

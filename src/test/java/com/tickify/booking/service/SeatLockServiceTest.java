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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeatLockService")
class SeatLockServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private SeatLockService seatLockService;

    private final UUID eventId = UUID.randomUUID();
    private final UUID seatId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);

        TickifyProperties properties = new TickifyProperties();
        properties.getSeatLock().setTtl(Duration.ofMinutes(10));

        seatLockService = new SeatLockService(redis, properties,
                new BookingMetrics(new SimpleMeterRegistry()));
    }

    private String expectedKey() {
        return "seatLock:event:" + eventId + ":seat:" + seatId;
    }

    @Test
    @DisplayName("acquires a seat with SET NX and the configured TTL")
    void acquiresSeatWithNxAndTtl() {
        when(valueOps.setIfAbsent(expectedKey(), userId.toString(), Duration.ofMinutes(10)))
                .thenReturn(true);

        assertThat(seatLockService.tryLockSeat(eventId, seatId, userId)).isTrue();

        // SET NX is the whole concurrency guarantee — assert we do not fall back to GET/SET.
        verify(valueOps).setIfAbsent(expectedKey(), userId.toString(), Duration.ofMinutes(10));
        verify(valueOps, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("refuses a seat another user already holds")
    void refusesContendedSeat() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(seatLockService.tryLockSeat(eventId, seatId, userId)).isFalse();
    }

    @Test
    @DisplayName("treats a null Redis reply as a failed acquisition")
    void nullReplyIsNotAnAcquisition() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(null);

        assertThat(seatLockService.tryLockSeat(eventId, seatId, userId)).isFalse();
    }

    @Test
    @DisplayName("reports ownership only for the user recorded in the lock")
    void isLockedByChecksTheStoredOwner() {
        when(valueOps.get(expectedKey())).thenReturn(userId.toString());

        assertThat(seatLockService.isLockedBy(eventId, seatId, userId)).isTrue();
        assertThat(seatLockService.isLockedBy(eventId, seatId, UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("reports no ownership when the lock has expired")
    void isLockedByIsFalseWhenLockGone() {
        when(valueOps.get(expectedKey())).thenReturn(null);

        assertThat(seatLockService.isLockedBy(eventId, seatId, userId)).isFalse();
    }

    @Test
    @DisplayName("owner-checked release goes through a compare-and-delete script, not a bare DEL")
    void ownerCheckedReleaseUsesScript() {
        seatLockService.releaseSeat(eventId, seatId, userId);

        // A plain DEL here would let a request whose lock already expired delete the lock
        // that a different user has since acquired.
        verify(redis).execute(any(RedisScript.class), eq(List.of(expectedKey())), eq(userId.toString()));
        verify(redis, never()).delete(anyString());
    }

    @Test
    @DisplayName("unconditional release deletes the key outright")
    void unconditionalReleaseDeletesKey() {
        seatLockService.releaseSeat(eventId, seatId);

        verify(redis).delete(expectedKey());
    }

    @Test
    @DisplayName("records contention on the metrics registry")
    void recordsContentionMetric() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TickifyProperties properties = new TickifyProperties();
        SeatLockService service = new SeatLockService(redis, properties, new BookingMetrics(registry));

        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);

        service.tryLockSeat(eventId, seatId, userId);
        service.tryLockSeat(eventId, seatId, UUID.randomUUID());

        assertThat(registry.get("tickify.seat.lock").tag("result", "acquired").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("tickify.seat.lock").tag("result", "contended").counter().count())
                .isEqualTo(1.0);
    }
}

package com.tickify.booking;

import com.tickify.booking.service.SeatLockService;
import com.tickify.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The claim the whole design rests on: whatever the demand, a seat is sold to exactly one person.
 *
 * <p>These tests run against a real Redis rather than a mock, because the guarantee comes from
 * Redis executing {@code SET NX} atomically — a mock would only prove the code calls the method.
 * Threads are released simultaneously from a latch so the requests genuinely overlap.
 */
@DisplayName("Seat lock under contention")
class SeatContentionIT extends IntegrationTestBase {

    @Autowired private SeatLockService seatLockService;
    @Autowired private StringRedisTemplate redis;

    @Test
    @DisplayName("200 simultaneous buyers of one seat produce exactly one winner")
    void oneSeatOneWinner() throws Exception {
        int contenders = 200;
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        AtomicInteger winners = new AtomicInteger();
        CountDownLatch startGun = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(Math.min(contenders, 64))) {
            List<Callable<Boolean>> attempts = IntStream.range(0, contenders)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        startGun.await(10, TimeUnit.SECONDS);
                        boolean won = seatLockService.tryLockSeat(eventId, seatId, UUID.randomUUID());
                        if (won) {
                            winners.incrementAndGet();
                        }
                        return won;
                    })
                    .toList();

            List<Future<Boolean>> futures = attempts.stream().map(pool::submit).toList();
            startGun.countDown();

            long succeeded = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }

            assertThat(succeeded)
                    .as("%d buyers raced for one seat; overselling means more than one winner", contenders)
                    .isEqualTo(1);
            assertThat(winners.get()).isEqualTo(1);
        } finally {
            redis.delete("seatLock:event:" + eventId + ":seat:" + seatId);
        }
    }

    @Test
    @DisplayName("the winner holds the seat until they release it")
    void winnerKeepsTheSeatUntilRelease() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();

        try {
            assertThat(seatLockService.tryLockSeat(eventId, seatId, winner)).isTrue();
            assertThat(seatLockService.tryLockSeat(eventId, seatId, loser)).isFalse();
            assertThat(seatLockService.isLockedBy(eventId, seatId, winner)).isTrue();

            seatLockService.releaseSeat(eventId, seatId, winner);

            assertThat(seatLockService.tryLockSeat(eventId, seatId, loser))
                    .as("the seat goes back on sale once released")
                    .isTrue();
        } finally {
            redis.delete("seatLock:event:" + eventId + ":seat:" + seatId);
        }
    }

    @Test
    @DisplayName("a loser cannot release the winner's lock")
    void loserCannotStealTheSeat() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();

        try {
            seatLockService.tryLockSeat(eventId, seatId, winner);

            // A losing request rolling back its own partial selection must not free this seat.
            seatLockService.releaseSeat(eventId, seatId, loser);

            assertThat(seatLockService.isLockedBy(eventId, seatId, winner))
                    .as("compare-and-delete keeps the lock with its owner")
                    .isTrue();
        } finally {
            redis.delete("seatLock:event:" + eventId + ":seat:" + seatId);
        }
    }

    @Test
    @DisplayName("each seat in a block is locked independently")
    void seatsAreLockedIndependently() {
        UUID eventId = UUID.randomUUID();
        List<UUID> seats = IntStream.range(0, 10).mapToObj(i -> UUID.randomUUID()).toList();
        UUID user = UUID.randomUUID();

        try {
            assertThat(seats).allMatch(seat -> seatLockService.tryLockSeat(eventId, seat, user));
            assertThat(seats).noneMatch(seat -> seatLockService.tryLockSeat(eventId, seat, UUID.randomUUID()));
        } finally {
            seats.forEach(seat -> redis.delete("seatLock:event:" + eventId + ":seat:" + seat));
        }
    }
}

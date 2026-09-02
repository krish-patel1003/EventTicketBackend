package com.tickify.booking.utils;

import com.tickify.booking.service.QueueService;
import com.tickify.config.properties.TickifyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Drains the virtual waiting room at a fixed rate.
 *
 * <p>This is the throttle for the whole system: however many users are queued, at most
 * {@code tickify.queue.promotion-batch-size} of them enter the booking window per tick.
 * Raising the batch size trades a shorter wait for more contention on the seat map.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueuePromoter {

    private final QueueService queueService;
    private final TickifyProperties properties;

    @Scheduled(fixedDelayString = "${tickify.queue.promotion-interval-ms:1000}")
    public void promoteBatch() {
        Set<String> activeEvents = queueService.getActiveEvents();

        if (activeEvents == null || activeEvents.isEmpty()) {
            return;
        }

        int batchSize = properties.getQueue().getPromotionBatchSize();

        for (String eventIdStr : activeEvents) {
            UUID eventId = UUID.fromString(eventIdStr);
            List<UUID> promoted = queueService.promote(eventId, batchSize);

            if (!promoted.isEmpty()) {
                log.info("Promoted {} users into the booking window for event {} ({} still waiting)",
                        promoted.size(), eventId, queueService.waitingCount(eventId));
            }
        }
    }
}

package com.project.event_ticket_backend.booking.utils;

import com.project.event_ticket_backend.booking.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QueuePromoter {

    private final QueueService queueService;

    private static final int BATCH_SIZE = 50; // configurable

    @Scheduled(fixedDelay = 1000) // run every 1s
    public void promoteBatch() {
        Set<String> activeEvents = queueService.getActiveEvents();

        if (activeEvents == null || activeEvents.isEmpty()) {
            return;
        }

        for (String eventIdStr : activeEvents) {
            UUID eventId = UUID.fromString(eventIdStr);
            List<UUID> promoted = queueService.promote(eventId, BATCH_SIZE);

            if (!promoted.isEmpty()) {
                System.out.printf("Promoted %d users for event %s%n", promoted.size(), eventId);
            }
        }
    }
}

package com.project.event_ticket_backend.booking.utils;

import com.project.event_ticket_backend.booking.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueuePromoter {

    private final QueueService queueService;

    @Scheduled(fixedDelay = 1000)
    public void promoteBatch() {
        // TODO
        // For each high-traffic event you’d iterate; demo uses one call site
        // queueService.promote(eventId, 200);
    }
}

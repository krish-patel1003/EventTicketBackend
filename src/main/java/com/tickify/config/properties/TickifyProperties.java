package com.tickify.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Tuning knobs for the parts of the system that behave differently under load.
 * Bound from the {@code tickify.*} block in application.yml.
 */
@Data
@ConfigurationProperties(prefix = "tickify")
public class TickifyProperties {

    private Cors cors = new Cors();
    private Queue queue = new Queue();
    private SeatLock seatLock = new SeatLock();
    private Booking booking = new Booking();
    private Seed seed = new Seed();

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Data
    public static class Queue {
        /** Users moved from the waiting room into the booking window per promoter tick. */
        private int promotionBatchSize = 50;
        /** How often the promoter runs. */
        private long promotionIntervalMs = 1000;
        /** How long a promoted user may hold their booking window before losing it. */
        private Duration slotTtl = Duration.ofMinutes(7);
    }

    @Data
    public static class SeatLock {
        /** How long a seat stays held after selection, before payment completes. */
        private Duration ttl = Duration.ofMinutes(10);
    }

    @Data
    public static class Booking {
        /**
         * Extra time allowed past the seat-lock TTL before a still-PENDING booking is
         * considered abandoned. Guards a payment that is merely slow, not lost.
         */
        private Duration reaperGracePeriod = Duration.ofMinutes(2);
    }

    @Data
    public static class Seed {
        private boolean enabled = true;
        private List<Venue> venues = new ArrayList<>();

        @Data
        public static class Venue {
            private String name;
            private String location;
            private int capacity;
        }
    }
}

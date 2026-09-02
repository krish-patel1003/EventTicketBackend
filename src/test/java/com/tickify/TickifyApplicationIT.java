package com.tickify;

import com.tickify.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application against real Postgres, Redis and RabbitMQ.
 *
 * <p>This is where the Flyway migrations meet the JPA mappings: the app runs with
 * {@code ddl-auto=validate}, so any drift between a migration and an entity fails here
 * rather than in production.
 */
@DisplayName("Application context")
class TickifyApplicationIT extends IntegrationTestBase {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("starts with the schema validated against the JPA mappings")
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBean(com.tickify.booking.service.BookingService.class)).isNotNull();
        assertThat(context.getBean(com.tickify.booking.service.SeatLockService.class)).isNotNull();
    }
}

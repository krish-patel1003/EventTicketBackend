package com.tickify.support;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for the end-to-end tests. These exercise the parts of the system that only
 * exist between the components — the Redis lock, the RabbitMQ saga, the Flyway schema —
 * and so they need the real backing services rather than mocks.
 *
 * <p>Two ways to get them:
 * <ul>
 *   <li><b>Testcontainers (default)</b> — Postgres, Redis and RabbitMQ are started on demand.
 *       Requires a working Docker daemon; the whole class is skipped when there is none, so
 *       {@code mvn verify} still succeeds on a machine without Docker.</li>
 *   <li><b>External</b> — run with {@code -Dtickify.it.external=true} to point the tests at a
 *       stack that is already running (see {@code scripts/dev-stack.sh}). This is what CI does
 *       when the services come from the pipeline rather than from Docker-in-Docker.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestFixtures.class)
@ActiveProfiles("integration-test")
@EnabledIf(value = "com.tickify.support.IntegrationTestBase#infrastructureAvailable",
        disabledReason = "needs Docker for Testcontainers, or -Dtickify.it.external=true")
public abstract class IntegrationTestBase {

    protected static final boolean EXTERNAL = Boolean.getBoolean("tickify.it.external");

    private static final PostgreSQLContainer<?> POSTGRES;
    private static final RedisContainer REDIS;
    private static final RabbitMQContainer RABBITMQ;

    static {
        if (EXTERNAL || !dockerAvailable()) {
            POSTGRES = null;
            REDIS = null;
            RABBITMQ = null;
        } else {
            POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
            REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"));
            RABBITMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

            POSTGRES.start();
            REDIS.start();
            RABBITMQ.start();
        }
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Referenced by {@link EnabledIf} above. */
    @SuppressWarnings("unused")
    public static boolean infrastructureAvailable() {
        return EXTERNAL || dockerAvailable();
    }

    @DynamicPropertySource
    static void backingServices(DynamicPropertyRegistry registry) {
        // Never let the test context try to boot compose.yaml on top of what we just started.
        registry.add("spring.docker.compose.enabled", () -> false);

        if (EXTERNAL) {
            registry.add("spring.datasource.url",
                    () -> System.getProperty("tickify.it.db.url", "jdbc:postgresql://localhost:5432/mydatabase"));
            registry.add("spring.datasource.username",
                    () -> System.getProperty("tickify.it.db.username", "myuser"));
            registry.add("spring.datasource.password",
                    () -> System.getProperty("tickify.it.db.password", "secret"));
            registry.add("spring.data.redis.host", () -> "localhost");
            registry.add("spring.data.redis.port", () -> 6379);
            registry.add("spring.rabbitmq.host", () -> "localhost");
            registry.add("spring.rabbitmq.port", () -> 5672);
            registry.add("spring.rabbitmq.username", () -> "myuser");
            registry.add("spring.rabbitmq.password", () -> "secret");
            return;
        }

        if (POSTGRES == null) {
            return; // The class is disabled; nothing will run.
        }

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }
}

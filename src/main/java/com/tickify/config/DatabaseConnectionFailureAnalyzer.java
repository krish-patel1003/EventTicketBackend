package com.tickify.config;

import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns the two ways Tickify most often fails to start into an explanation.
 *
 * <p>Both surface as a Flyway/Hibernate bean-creation failure wrapping a raw PostgreSQL
 * error, several hundred lines deep in a stack trace. Neither message says what is actually
 * wrong, and the first is actively misleading:
 *
 * <ul>
 *   <li>{@code FATAL: role "myuser" does not exist} — the app reached a <em>real</em>
 *       PostgreSQL server, just not the one from {@code compose.yaml}. Almost always a
 *       Postgres already installed on the host is holding port 5432, so the container could
 *       not bind it (or Docker was not running at all and the default URL found the host's
 *       server). It reads like a credentials problem; it is a port collision.</li>
 *   <li>{@code Connection refused} — nothing is listening at all, so the backing services
 *       were never started.</li>
 * </ul>
 *
 * <p>Spring Boot's {@code FailureAnalyzer} hook exists for exactly this: replace the trace
 * with something the reader can act on. Registered in {@code META-INF/spring.factories}.
 */
public class DatabaseConnectionFailureAnalyzer extends AbstractFailureAnalyzer<BeanCreationException> {

    /** SQLState 28000: invalid authorization specification. */
    private static final String INVALID_AUTHORIZATION = "28000";
    /** SQLState 08001: the client could not establish the connection. */
    private static final String UNABLE_TO_CONNECT = "08001";

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, BeanCreationException cause) {
        PSQLException postgres = findPostgresCause(rootFailure);
        if (postgres == null) {
            return null; // Not a database problem; let Spring report it normally.
        }

        String state = postgres.getSQLState();
        String message = String.valueOf(postgres.getMessage());

        if (INVALID_AUTHORIZATION.equals(state) && message.contains("does not exist")) {
            return new FailureAnalysis(
                    """
                    Tickify connected to a PostgreSQL server, but that server does not have the \
                    role it expects:

                        %s

                    This is not a password problem. It means the database on this port is NOT the \
                    one from compose.yaml -- most often a PostgreSQL already installed on this \
                    machine (Homebrew, Postgres.app, an older container) is holding port 5432, so \
                    Tickify's container could not bind it. Docker not running has the same effect: \
                    the app falls back to its default URL and finds the host's server instead."""
                            .formatted(message),
                    """
                    Check what is listening first:

                        docker ps                          # is tickify-postgres actually up?
                        lsof -nP -iTCP:5432 -sTCP:LISTEN   # macOS/Linux: who owns the port

                    Then pick one:

                    A. Free the port for Docker, then start again:
                         brew services stop postgresql@16   # or your equivalent
                         docker compose up -d

                    B. Keep your PostgreSQL and move Tickify's onto spare ports:
                         POSTGRES_PORT=55432 REDIS_PORT=56379 RABBITMQ_PORT=55672 docker compose up -d
                         DB_URL=jdbc:postgresql://localhost:55432/mydatabase \\
                           REDIS_PORT=56379 RABBITMQ_PORT=55672 java -jar target/tickify-1.0.0.jar

                    C. Use your own PostgreSQL by creating what Tickify expects:
                         psql -U postgres -c "CREATE USER myuser WITH PASSWORD 'secret' SUPERUSER;"
                         psql -U postgres -c "CREATE DATABASE mydatabase OWNER myuser;"
                         docker compose up -d redis rabbitmq mailhog

                    See the Troubleshooting section of README.md.""",
                    postgres);
        }

        if (UNABLE_TO_CONNECT.equals(state) || message.contains("Connection refused")) {
            return new FailureAnalysis(
                    """
                    Tickify could not reach PostgreSQL at all:

                        %s

                    Nothing is listening on that address, so the backing services are not running."""
                            .formatted(message),
                    """
                    Start them, then run the app again:

                        docker compose up -d               # Postgres, Redis, RabbitMQ, MailHog
                        java -jar target/tickify-1.0.0.jar

                    Without a Docker daemon, ./scripts/dev-stack.sh start runs them as native
                    processes instead.

                    If they are on non-default ports, point the app at them with DB_URL,
                    REDIS_PORT and RABBITMQ_PORT -- see README.md.""",
                    postgres);
        }

        return null;
    }

    private PSQLException findPostgresCause(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof PSQLException postgres) {
                return postgres;
            }
            if (current.getCause() == current) {
                break; // Defensive: a self-referencing cause would loop forever.
            }
        }
        return null;
    }
}

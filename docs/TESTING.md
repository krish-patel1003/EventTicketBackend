# Testing

```bash
./mvnw test                                                   # 68 tests, no infrastructure needed
./mvnw verify -Pintegration-tests                             # + 10 end-to-end, via Testcontainers
./mvnw verify -Pintegration-tests -Dtickify.it.external=true  # ...or against a running stack
```

`./mvnw test` is hermetic: no Docker, no database, seconds to run. The end-to-end tests are
opt-in, and skip themselves cleanly when there is no Docker daemon, so `mvn verify` never fails
for want of infrastructure.

---

## What is worth testing here

A ticketing system has a small number of properties that must never break, and a large number of
endpoints that are ordinary CRUD. The suite is weighted accordingly — it goes after the
properties:

- a seat cannot be sold to two people;
- a losing shopper gets `409`, never `500` and never a timeout;
- a redelivered message cannot mint a second ticket;
- a QR code cannot admit two people;
- a declined payment puts the seats back on sale — and leaves them sellable;
- `@PreAuthorize` is actually enforced.

Every one of those has a test that fails if the mechanism behind it is removed.

---

## The layers

### Unit tests — `src/test/java/**/*Test.java`

Mockito, no Spring context. These pin down the logic that the design rests on.

| Class | What it fixes in place |
|---|---|
| `SeatLockServiceTest` | Locking uses `SET NX` with a TTL, not a get-then-set. Release is compare-and-delete, so a request whose lock expired cannot free somebody else's seat. |
| `QueueServiceTest` | FIFO by arrival time; re-joining keeps your place; promotion issues a TTL slot and dequeues; a drained event stops being polled. |
| `BookingServiceTest` | The waiting-room gate; all-or-nothing locking; a failed write hands its locks back rather than stranding seats for the full TTL. |
| `PendingBookingWriterTest` | The booking is saved before its seats; money is `BigDecimal` (10.10 × 3 = 30.30, not 30.299999…); a live-claim collision surfaces as conflict, not as a server fault. |
| `PaymentCompletedConsumerTest` | Success reserves before unlocking; failure releases both the lock *and* the seat claim; a redelivery is ignored; a late failure cannot cancel a confirmed booking. |
| `AbandonedBookingReaperTest` | Abandoned checkouts release both halves; the cutoff leaves room for a slow payment so one is never reaped mid-flight. |
| `QRCodeServiceTest` | Single use, at the scanner and by booking reference alike. |
| `UserRegistrationServiceTest` | Passwords are bcrypt-hashed; `ADMIN` cannot be self-assigned. |

### Web-slice tests — `@WebMvcTest`

`BookingControllerTest`, `QueueControllerTest`. These test the HTTP contract the frontend and the
load test both branch on — that contention is `409`, an expired booking window is `403`, an
unknown booking is `404`, and a malformed UUID is `400` rather than `500`.

Security auto-configuration is excluded so the slice tests exactly one thing. Authorization is
covered separately, below and end to end.

### Security tests — `SecurityConfigurationTest`

This one exists because of two real defects, both silent:

- `@EnableMethodSecurity` was missing, so Spring registered no method interceptor and **every
  `@PreAuthorize` on every controller was ignored**;
- the staff controller carried `@PreAuthorize("HAS_STAFF")`, which is not an expression — it
  parses as a property reference and evaluates to null.

Neither fails at startup. Neither shows up in a normal test. So the suite asserts, reflectively,
that method security is enabled and that every `@PreAuthorize` expression parses *and* performs a
real authority check.

### End-to-end tests — `src/test/java/**/*IT.java`

Real Postgres, real Redis, real RabbitMQ. These cover what only exists *between* components.

| Class | What it proves |
|---|---|
| `BookingFlowIT` | The whole journey over HTTP: register → queue → admission → lock → pay → poll to `SUCCESS` → scan at the gate. Then scans the same ticket again and asserts it is refused. Also asserts the seat map is closed without an admission slot, and that role checks are genuinely enforced. |
| `SeatContentionIT` | 200 threads released from a latch race for one seat. Exactly one wins. Plus: the winner holds it until release, a loser cannot steal it, and seats in a block lock independently. |
| `TickifyApplicationIT` | The context starts with `ddl-auto=validate`, so drift between a Flyway migration and a JPA mapping fails the build. |

`SeatContentionIT` runs against a real Redis on purpose. Mocking it would only prove the code
calls `setIfAbsent`; the guarantee comes from Redis executing that command atomically.

---

## Running the integration tests without Docker

`IntegrationTestBase` supports two modes:

```bash
# Testcontainers (default) — needs a Docker daemon
./mvnw verify -Pintegration-tests

# Against an already-running stack — CI where services come from the pipeline,
# or a sandbox with no Docker at all
./scripts/dev-stack.sh start
./mvnw verify -Pintegration-tests -Dtickify.it.external=true
```

With neither available the whole class is disabled by `@EnabledIf`, with a reason, rather than
failing.

The `integration-test` profile turns the system's own timing down so the tests are fast and
deterministic: the promoter runs every 200 ms instead of every second, the simulated payment
latency is zero, and venue seeding is off — each test creates the venue it needs at the size it
needs, through `TestFixtures`.

---

## Coverage

JaCoCo runs on every `./mvnw test`; the report lands at `target/site/jacoco/index.html`.

No coverage threshold is enforced, deliberately. The number would be dominated by DTOs, mappers
and Lombok-generated accessors, and pushing it up means writing tests for those rather than for
the concurrency behaviour that actually matters. The list at the top of this page is the real
target.

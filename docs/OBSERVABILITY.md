# Observability

Three things, each answering a different question when a drop is in progress:

| | Question it answers |
|---|---|
| **Correlation IDs** | What happened to *this* request? |
| **Structured logs** | What happened across all requests, and why? |
| **Metrics** | Is the drop healthy right now? |

---

## Correlation IDs

`CorrelationIdFilter` runs before everything else — including Spring Security, so authentication
failures are correlated too. Every request gets an id, put in the SLF4J MDC and echoed back on
the response:

```
X-Correlation-Id: 6cb4712e-24f1-4451-9c23-a144ff7b2323
```

A client may supply its own; otherwise one is generated. Because the id is on the response, a
user reporting "it said the seat was gone" can quote it, and every log line for that request is
one grep away:

```
17:59:18.370  INFO [11519c9e-…] BookingService : Created pending booking 45e42b26-… (TICK-CD73876A)
17:59:18.434  INFO [7cd37bf1-…] BookingService : Published payment.requested for booking 45e42b26-…
```

The saga's own log lines carry the booking id rather than a correlation id, because they run on a
RabbitMQ consumer thread with no request behind them. The booking id joins the two halves.

---

## Logging

`RequestLoggingFilter` emits one access-log line per request — method, path, status, duration,
caller — at a level chosen by outcome: `INFO` under 400, `WARN` for 4xx, `ERROR` for 5xx. Health
checks and metrics scrapes are excluded, or they would be most of the volume.

Bodies are never logged. They carry passwords and base64 QR payloads, neither of which belongs in
a log aggregator.

Two output formats, chosen by profile (`logback-spring.xml`):

**Default** — human-readable, correlation id inline:

```
18:02:22 INFO [6cb4712e-24f1-4451-9c23-a144ff7b2323] c.t.booking.service.BookingService : Created pending booking …
```

**`json-logs`** — one JSON object per line, for Loki/ELK/CloudWatch:

```json
{"@timestamp":"2026-09-02T18:02:22.738Z","level":"INFO","service":"tickify",
 "correlationId":"6cb4712e-…","httpMethod":"POST","httpPath":"/api/v1/bookings/lock",
 "httpStatus":"200","durationMs":"43","user":"alice@example.com",
 "message":"POST /api/v1/bookings/lock -> 200 (43ms)"}
```

```bash
SPRING_PROFILES_ACTIVE=json-logs java -jar target/tickify-1.0.0.jar
```

Levels are tunable per environment (`LOG_LEVEL_APP`, `LOG_LEVEL_SQL`, `LOG_LEVEL_SECURITY`) and
at runtime through `/actuator/loggers`.

---

## Metrics

Actuator plus Micrometer, exported at `/actuator/prometheus`.

Spring gives you `http.server.requests` for free, and that tells you *that* a spike happened. It
does not tell you **why**. These domain meters do — they track the two things that decide whether
a drop holds up: seat-lock contention and the payment saga's outcome.

| Meter | Meaning |
|---|---|
| `tickify_seat_lock_total{result="acquired"\|"contended"}` | The contention ratio. Rising `contended` means demand is concentrating on the same seats — consider admitting smaller batches. |
| `tickify_seat_lock_duration_seconds` | Redis lock latency, with p50/p95/p99. If this climbs, Redis is the bottleneck. |
| `tickify_queue_joined_total` / `tickify_queue_promoted_total` | Arrival rate vs. admission rate. The gap is the waiting room's depth. |
| `tickify_booking_total{state="pending"\|"confirmed"\|"failed"}` | The checkout funnel. `pending` far above `confirmed + failed` means the saga is backing up. |

From the load-test runs in [LOAD_TESTING.md](LOAD_TESTING.md):

```
tickify_seat_lock_total{result="acquired"}   8952
tickify_seat_lock_total{result="contended"}  3813     # 30% contention — a real drop
tickify_queue_joined_total                   7108
tickify_queue_promoted_total                 6885     # queue keeping up with arrivals
tickify_booking_total{state="confirmed"}     3489
tickify_booking_total{state="failed"}         383     # ~10%, matching the simulated decline rate
```

---

## Health

```
/actuator/health          aggregate, with per-component detail
/actuator/health/liveness  restart me?
/actuator/health/readiness send me traffic?
```

Postgres, Redis and RabbitMQ are all in the aggregate — the service genuinely cannot function
without any of them.

**Mail is deliberately excluded** (`management.health.mail.enabled=false`). Outbound SMTP is
best-effort here: a mail outage delays confirmation e-mails, but tickets are still valid at the
gate, so it must not pull the instance out of the load balancer.

---

## What is missing

**Distributed tracing.** The correlation id is threaded through HTTP and carried into the saga's
logs by booking id, but there is no trace context propagated across the RabbitMQ hop. Micrometer
Tracing with a W3C `traceparent` header on published messages would make the whole checkout —
HTTP request, payment consumer, confirmation consumer — a single trace in Tempo or Jaeger. That
is the next thing I would add.

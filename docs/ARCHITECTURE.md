# Architecture

Tickify exists to answer one question: **how do you sell 2,000 seats to 50,000 people at
10:00:00 without ever selling the same seat twice?**

Everything below follows from that. The interesting parts are not the CRUD endpoints — they are
the three mechanisms that keep the system correct and responsive when demand is a hundred times
capacity, and the specific failure each one is there to prevent.

---

## 1. The virtual waiting room

**Problem.** If every arrival is admitted to the seat map at once, the seat-availability query,
the lock traffic and the connection pool all take the same spike. The site does not degrade
gracefully; it falls over, and it does so for everybody at once.

**Mechanism.** Arrivals are parked in a Redis sorted set scored by arrival time, and a scheduled
promoter admits a fixed batch per tick.

```
queue:event:{id}                ZSET   waiting users, score = enqueue millis (FIFO)
queue:event:{id}:active         SET    users currently inside the booking window
queue:event:{id}:slot:{userId}  STRING the admission token itself — with a TTL
queue:activeEvents              SET    which events the promoter needs to work on
```

The load reaching the booking path is therefore bounded by `promotion-batch-size ÷
promotion-interval-ms`, not by how many people showed up. Raising the batch size trades a shorter
wait for more contention on the seat map; it is one number in configuration.

**Why the slot is a TTL key.** The booking window has to expire on its own. If admission were a
row in a table, every abandoned checkout would need a reaper to notice it, and until then it
would occupy a slot that a real shopper could have used. As a Redis key with a TTL, the window
closes by itself and the set of active shoppers is self-limiting with no bookkeeping.

`join` is idempotent: re-joining an event you are already queued for returns your original
position rather than sending you to the back. Refreshing the page must not cost you your place.

---

## 2. Distributed seat locks

**Problem.** Two people click seat A-12 in the same millisecond. A read-then-write
(`SELECT ... WHERE is_locked = false`, then `UPDATE`) has a window between the two statements
where both see the seat as free. Closing that window in the database means row locks or
`SELECT FOR UPDATE` on the hottest rows in the system, precisely when the system is busiest.

**Mechanism.** A single Redis command:

```
SET seatLock:event:{eventId}:seat:{seatId} {userId} NX PX 600000
```

Redis executes commands one at a time, so `SET NX` is the arbitration. The first request to
arrive wins; every other request is told `409 Conflict`. The database is never asked to referee,
so a ticket drop never turns into row-lock contention on `event_seats`.

Selecting several seats is **all-or-nothing**. If any seat in the block is taken, the locks
already acquired in that request are handed back — using a compare-and-delete Lua script, never a
bare `DEL`:

```lua
if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end
```

Without the ownership check, a request whose own lock had already expired could delete the lock
that somebody else has since acquired — handing that person's seat to a third party.

**The TTL is the abandonment story.** A shopper who closes the tab after selecting seats releases
them automatically. There is no cleanup job for the Redis half.

### The database is still the final arbiter

The Redis lock is fast, not durable. If Redis restarted mid-drop, every lock would vanish. So the
guarantee is also written into the schema:

```sql
CREATE UNIQUE INDEX uk_booking_seats_live_claim
    ON booking_seats (seat_id) WHERE released_at IS NULL;
```

One *live* claim per seat, enforced by Postgres, whatever the application believes. A booking
that loses that race is reported as `409`, exactly like losing the Redis race — it is contention,
not a fault.

This index replaced a plain `UNIQUE(seat_id)`, which conflated "claimed once" with "claimed
now". See [the seat-release bug](#the-seat-release-bug) below.

---

## 3. The booking saga

**Problem.** A card processor takes seconds and sometimes declines. Holding an HTTP thread — and
a database transaction — open for that is how a thread pool dies under load.

**Mechanism.** Checkout is asynchronous, over RabbitMQ:

```
POST /bookings/lock              seats locked in Redis, PENDING booking written
POST /bookings/payment/initiate  publish payment.requested → 200 immediately
  ↓ payment service consumes, calls the provider, publishes payment.completed
  ↓ booking service consumes payment.completed
      SUCCESS → mark seats reserved, mint QR, publish booking.confirmed
      FAILED  → release the Redis locks and the seat claims
  ↓ notification service consumes booking.confirmed, sends the e-mail
GET /bookings/{id}               client polls until SUCCESS or FAILED
```

**Ordering on success matters.** The durable reservation (`event_seats.is_reserved = true`) is
written *before* the Redis lock is dropped. Reversed, there would be a window in which the seat
is neither locked nor reserved — and therefore sellable twice.

**At-least-once delivery is assumed.** RabbitMQ can and will redeliver `payment.completed`. The
consumer checks that the booking is still `PENDING` before acting, so a redelivery cannot mint a
second QR code, and a late `FAILED` cannot cancel a booking that already succeeded.

**Poison messages are dropped, not requeued.** `defaultRequeueRejected(false)` — the default of
requeueing turns one unprocessable message into an unbounded redelivery loop that saturates the
consumers and starves everything behind it. (This was not theoretical; see below.)

**Notifications are best-effort.** The confirmation e-mail is sent inside a `try/catch` that
swallows everything. The ticket is already valid at the gate by then; a mail outage must not fail
a paid booking.

---

## 4. Authentication

Stateless RSA-signed JWTs. The access token carries the caller's `roles` and `uid`, so
authorization needs no database round-trip and neither does resolving "who is calling" on the hot
path.

```json
{ "iss": "tickify-auth", "sub": "alice@example.com",
  "uid": "d44ac2b3-…", "roles": ["USER"], "exp": 1788372162 }
```

Access tokens live 15 minutes; refresh tokens are rows in `refresh_tokens` and can be revoked.
The trade-off is the usual one: a role revoked mid-session is not felt until the access token
expires, and 15 minutes bounds that.

`@EnableMethodSecurity` on `SecurityConfig` is what makes `@PreAuthorize` enforce anything —
without it Spring registers no method interceptor and every annotation is silently inert.
`SecurityConfigurationTest` asserts both that it is present and that every expression actually
performs an authority check.

---

## Data model

```
users ──< users_roles >── roles
  │
  └──< refresh_tokens

venues ──< venue_seats                    layout template
  │
events ──< event_seats                    per-event copy of the layout
  │           │
  │           └── ticket_type_id          price tier for this seat
  ├──< ticket_types
  └──< bookings ──< booking_seats         one LIVE claim per seat
           └──< qr_codes                  single-use, burned at the gate
```

An event **copies** its venue's seat layout into `event_seats` when it is created. Seats are
per-event state — locked, reserved, priced — so they cannot be shared across the events at a
venue.

Schema is owned by Flyway; the app boots with `ddl-auto=validate`, so any drift between a
migration and a JPA mapping fails at startup rather than in production. That check earns its
keep: it caught `TicketType.price` being a Java `double` against a `DECIMAL(10,2)` column —
money in binary floating point — which is now `BigDecimal`.

---

## Bugs this design caught

Three of these were found by the load tests and one by the test suite. They are documented
because the mechanism that caught each one is part of the architecture.

### The seat-release bug

A declined payment released the Redis lock but left the `booking_seats` row in place. The seat
went back on sale, and the next buyer's insert collided with `UNIQUE(seat_id)` — a `500`, and a
seat that could never be sold again for the rest of the event.

The fix was to make "claimed" and "claimed *now*" different things: `released_at`, plus a partial
unique index over live claims only. Both halves of a release — the Redis lock and the database
claim — now expire together, and `AbandonedBookingReaper` sweeps up checkouts that were started
and never finished.

The plain unique constraint was doing its job perfectly: it refused to let the same seat be sold
twice, loudly, instead of quietly overselling.

### Two authentication mechanisms, one winner

A hand-written JWT filter loaded roles from the database, and Spring's OAuth2 resource server
derived authorities from token scopes. Both wrote the `SecurityContext`; the resource server's
filter ran last and replaced the database roles with the token's (empty) scopes. Every
`hasRole(...)` check failed for everybody — invisible until `@EnableMethodSecurity` was turned on
and the checks started running at all. Now there is one mechanism: the `roles` claim.

### The message converter that was never configured

Spring AMQP's default `SimpleMessageConverter` handles only `String`, `byte[]` and
`Serializable`. Every message in the saga is a Java record, so every publish was rejected and no
payment was ever processed. A `Jackson2JsonMessageConverter` on both the template and the
listener container fixed it — and the end-to-end test that now covers the flow would have caught
it on day one.

---

## What I would do next

Honest list of what this does not yet do, in the order I would tackle it.

1. **Dead-letter queues.** Poison messages are currently dropped. They should go to a DLQ with
   the failure attached, so nothing is lost silently.
2. **Ticket-type inventory.** `ticket_types.available_quantity` is never decremented. Seat-level
   locking makes it non-binding today, but a general-admission tier without assigned seats would
   need it.
3. **Idempotency keys on `/bookings/lock`.** A client retry after a timeout can currently produce
   a second pending booking.
4. **Waiting-room fairness across instances.** The promoter runs on every node; with more than
   one, the effective admission rate is multiplied. It needs a lock or a single-writer election.
5. **Rate limiting** on registration and login — bcrypt is expensive by design, which makes those
   endpoints the cheapest denial-of-service target in the system.

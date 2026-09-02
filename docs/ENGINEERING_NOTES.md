# Engineering notes

Decisions, trade-offs, and the reasoning behind them. Where a choice has a real cost, the cost is
stated rather than glossed over.

---

## Why Redis for seat locks, when the database has row locks

`SELECT ... FOR UPDATE` on `event_seats` would also prevent double-selling. It would also put the
hottest rows in the system under exclusive lock at exactly the moment the system is busiest,
serialising the transactions behind them and holding database connections while a shopper thinks.

`SET NX PX` moves the arbitration to a component whose whole job is single-threaded, in-memory
operations, and gives the lock a TTL for free — which is what makes an abandoned checkout
self-healing without a reaper.

**The cost:** two systems must agree. Redis is fast but not durable; if it restarted mid-drop,
every lock would vanish. So the guarantee is *also* in the schema, as a partial unique index over
live claims. Redis is the fast path; Postgres is the truth. Keeping the two in step is the
subtlest part of this codebase, and it is where the load tests found real bugs — see
[LOAD_TESTING.md](LOAD_TESTING.md).

## Why the waiting room is Redis keys rather than a table

The booking window has to close by itself. As a row, every abandoned checkout needs something to
notice it; until then it occupies a slot a real shopper could use. As a key with a TTL, the
window expires on its own and the active set is self-limiting.

The admission *queue* is a sorted set scored by arrival time, which gives FIFO ordering, O(log n)
rank lookups for "you are number 1,247", and range reads for batch promotion — all in one
structure.

**The cost:** the promoter runs on every application instance, so with more than one node the
effective admission rate multiplies. Single-instance today; it needs a leader election or a
distributed lock before it scales out. Listed in [ARCHITECTURE.md](ARCHITECTURE.md#what-i-would-do-next).

## Why checkout is a saga rather than a synchronous call

Card processors take seconds and sometimes decline. A synchronous checkout holds a web thread and
a database transaction for the whole round trip; at a few hundred concurrent shoppers, the pool
is gone.

Publishing `payment.requested` and returning `200` immediately means the HTTP layer is free
regardless of how slow the provider is. The client polls.

**The cost:** eventual consistency, visible to the user as "awaiting payment", and every message
handler has to be idempotent because RabbitMQ delivers at least once. The consumer checks the
booking is still `PENDING` before acting, and `PaymentCompletedConsumerTest` pins that down.

## Why roles live in the JWT

An earlier version authenticated with a bearer token but resolved authorities from a database
lookup on every request — two mechanisms doing one job. They conflicted: both wrote the
`SecurityContext`, the resource server's filter ran last, and it replaced the database roles with
the token's empty scopes. Every role check failed for everybody, silently, because
`@EnableMethodSecurity` was also missing and no check was running at all.

Now there is one mechanism: `roles` and `uid` claims in the token. Authorization needs no
database round-trip, and neither does resolving the caller's id on the hot path.

**The cost:** a role revoked mid-session is not felt until the access token expires. Access
tokens live 15 minutes, which bounds it. Refresh tokens are rows and can be revoked immediately.

## Why money is `BigDecimal`

`TicketType.price` was a Java `double` against a `DECIMAL(10,2)` column. Booting with
`ddl-auto=validate` refused to start, which is the check doing its job. `10.10 × 3` is
`30.299999999999997` in binary floating point; `BookingServiceTest` asserts it comes out as
`30.30`.

## Why `ddl-auto=validate` and not `update`

`update` silently patches the schema to match the entities, which means the migrations stop being
the source of truth and two environments can quietly diverge. `validate` turns drift into a
startup failure. It found the `double`/`DECIMAL` mismatch above and a missing audit column on
`booking_seats` on the first boot.

## Why no UI framework on the frontend

The seat map is a grid of up to a couple of thousand interactive cells, memoised and grouped by
section and row. That wants direct control over the DOM and the styling, not a component library
fighting for it. The rest of the app is a handful of screens, so a design-token stylesheet
(~250 lines, light and dark) was less code than configuring a framework.

## Why the load test provisions its accounts up front

The first runs were measuring bcrypt. Password hashing is expensive on purpose, and registering a
new account per iteration consumed most of the box's CPU — the queue and lock latencies being
reported were mostly hashing. Provisioning accounts before the measured window changed the
throughput figure by 6.3× without touching the application.

Both variants are kept, because they answer different questions: `FRESH_USERS=true` measures the
true first-visit funnel including sign-up, and the default isolates the booking machinery.

## Why `409` is a first-class outcome

Losing a race for a seat is the normal, correct behaviour of a ticketing system under load — in
the drop scenario it happened 3,304 times in 75 seconds. Treating it as an error would make a
healthy run look broken, so:

- the service throws a distinct exception mapped to `409`, not a generic failure;
- k6 registers `409` as an expected status so it does not inflate `http_req_failed`;
- the frontend catches it specifically and refreshes the seat map with a plain-English message
  rather than a generic error;
- `500` is reserved for genuine faults, and the load tests assert there are none.

---

## Things I know are missing

Roughly in the order I would do them.

1. **Dead-letter queues.** Poison messages are dropped (`defaultRequeueRejected(false)`) rather
   than requeued forever. Correct, but they should land in a DLQ with the failure attached.
2. **Distributed tracing.** Correlation IDs cover HTTP; the RabbitMQ hop breaks the chain.
   Micrometer Tracing with `traceparent` on published messages would make a whole checkout one
   trace.
3. **Ticket-type inventory.** `available_quantity` is never decremented. Seat-level locking makes
   it non-binding today, but general admission without assigned seats would need it.
4. **Idempotency keys on `/bookings/lock`.** A client retry after a timeout can currently create a
   second pending booking.
5. **Rate limiting** on `/auth/register` and `/auth/login`. bcrypt is expensive by design, which
   makes them the cheapest DoS target in the system — as the load test demonstrated accidentally.
6. **Multi-instance promoter.** See above.
7. **`getAllActiveEvents` does not filter on `is_active`.** It returns everything, despite the
   name and the API docs. Left as-is rather than changed silently, because fixing it changes what
   existing clients see; it needs a deliberate decision about paging and filtering.

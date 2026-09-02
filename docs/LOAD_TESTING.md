# Load testing

Three [k6](https://k6.io) scenarios, in `loadtest/scenarios/`. Every number on this page came
from an actual run; the raw k6 summaries are committed under `loadtest/results/`.

```bash
./loadtest/run.sh                 # seeds an event, runs all three
./loadtest/run.sh ticket-drop     # just one
```

---

## Reference environment

Everything ran on **one box**: the load generator, the application, PostgreSQL, Redis and
RabbitMQ all sharing the same 4 vCPUs.

| | |
|---|---|
| CPU / RAM | 4 vCPU, 15 GB |
| OS / JVM | Ubuntu 24.04, OpenJDK 21, virtual threads enabled |
| Backing services | PostgreSQL 16, Redis 7, RabbitMQ 3.12 — native processes, not containers |
| App profile | `loadtest` (JSON logs, no e-mail verification), 300 ms simulated payment latency, 90% authorisation rate |
| Load generator | k6 v0.54, on the same host |

**Read the absolute throughput with that in mind.** k6 competes with the system under test for
the same four cores, so these figures are a floor, not a capacity estimate for real hardware.
What they *do* establish reliably is correctness under contention, the shape of the latency
distribution, and the size of each optimisation — all of which are measured against themselves.

---

## Scenario 1 — Seat contention

*Correctness, not throughput.* 150 virtual users all try to lock **the same seat** at the same
moment. Exactly one may win.

```bash
k6 run -e EVENT_ID=… -e TICKET_TYPE_ID=… -e VUS=150 loadtest/scenarios/seat-contention.js
```

```
     checks.........................: 100.00% 301 out of 301
     http_req_failed................: 0.00%   0 out of 932
     iterations.....................: 150     15.33/s
     seat_refused...................: 149
   ✓ seat_won.......................: 1
   ✓ unexpected_status..............: 0
```

**150 buyers, one seat, one winner, 149 clean `409`s, zero unexpected statuses.**

The assertion is a k6 threshold — `seat_won: ['count==1']` — so this fails the build rather than
merely reporting a number. Replace the Redis `SET NX` with a read-then-write and this run
oversells and goes red.

---

## Scenario 2 — Ticket drop

The full journey, for 200 concurrent shoppers over 75 seconds: queue → wait for admission →
load the seat map → pick 2 seats at random → lock → pay → poll until the saga settles. Seats are
picked at random precisely so that shoppers collide.

```bash
node loadtest/seed.mjs --users 400 --out loadtest/results/users.json
k6 run -e EVENT_ID=… -e TICKET_TYPE_ID=… -e PEAK_VUS=200 loadtest/scenarios/ticket-drop.js
```

```
     checks.........................: 100.00% 92371 out of 92371
     http_req_failed................: 0.00%   0 out of 101514
     http_reqs......................: 101514  1353.37/s
     iterations.....................: 45125   601.60/s

   ✓ { name:queue_join }............: p(95)=178.29ms  p(99)=232.19ms  max=452.75ms
   ✓ { name:queue_status }..........: p(95)=148.86ms  p(99)=206.02ms  max=308.11ms
   ✓ { name:seat_lock }.............: p(95)=257.67ms  p(99)=368.88ms  max=571.45ms
   ✓ { name:seats_available }.......: p(95)=197.32ms  p(99)=276.68ms  max=541.83ms

     seats_won......................: 2225
     seat_contention_409............: 3304
     booking_confirm_ms.............: p(95)=3.62s
   ✓ unexpected_errors..............: 0
```

**101,514 requests, zero failures, zero unexpected errors.** 2,225 seats sold and 3,304 shoppers
correctly told the seat had gone. `booking_confirm_ms` is end-to-end saga latency — publish
`payment.requested` to observing `SUCCESS` — dominated by the 300 ms simulated provider call plus
queue time.

`409` is registered with k6 as an expected status
(`http.setResponseCallback(http.expectedStatuses({min:200,max:399}, 409))`). Losing a race for a
seat is the system working, and counting it as a transport failure would make a healthy run look
broken.

---

## Scenario 3 — Browse baseline

The read path, which is most traffic even during a drop. If listing events is already slow,
nothing measured on the write path means much.

```
     checks.........................: 100.00% 25960 out of 25960
   ✓ http_req_failed................: 0.00%   0 out of 25961
     http_reqs......................: 25961   568.50/s
   ✓ { name:events_list }...........: p(95)=17.49ms  p(99)=106.32ms  max=394.62ms
   ✓ { name:me }....................: p(95)=10.43ms  p(99)=66.36ms   max=341.32ms
```

---

## What the load tests found

Three real defects. Finding them is the reason the suite exists.

### 1. An N+1 query on the events list

The first browse run failed its threshold:

```
✗ { name:events_list }: avg=160.76ms  p(95)=574.85ms  p(99)=1.30s  max=2.90s
```

`EventMapper` looked up ticket types **per event**, so listing 20 events issued 21 queries. The
fix batches them into one `findByEvent_IdIn` and groups in memory. Re-run back to back:

| `GET /api/v1/events/` | avg | p95 | p99 | throughput |
|---|---|---|---|---|
| Before | 160.8 ms | 574.9 ms | 1.30 s | 411 req/s |
| After | 28.2 ms | **107.2 ms** | 297.7 ms | **505 req/s** |

*(The 17 ms p95 quoted in Scenario 3 is a later run on a warm JVM after the other fixes below;
the pair above is the like-for-like measurement of this change alone.)*

### 2. A declined payment made a seat permanently unsellable

The first ticket-drop run reported **74 unexpected errors**, all `500`s on `/bookings/lock`:

```
ERROR: duplicate key value violates unique constraint "booking_seats_seat_id_key"
```

A failed payment released the Redis lock but left the `booking_seats` row behind. The seat went
back on sale and then could never be sold — every subsequent buyer hit the constraint.

Fixing it needed `released_at` plus a partial unique index over live claims only, releasing the
claim alongside the lock, and a reaper for checkouts that are simply abandoned. That took the
count from **74 → 14**.

The remaining 14 were a second path to the same place: **confirming** a booking also releases its
Redis lock, so a client holding a stale seat map could re-lock a seat that had already been sold.
Adding a reserved-seat check, and translating a live-claim collision into `409` rather than
letting it escape as a `500`, took it to **zero**.

| Ticket drop, 200 VUs | unexpected errors | booking success |
|---|---|---|
| Initial | 74 | 67.8% |
| After releasing seat claims | 14 | 76.7% |
| After closing the sold-seat race | **0** | 76.8% |

### 3. The load test was measuring bcrypt, not booking

Even with zero errors, the queue endpoints missed their latency thresholds by 3–5×. The cause was
in the test, not the system: each iteration registered a new account, and password hashing is
bcrypt — expensive on purpose. A few hundred sign-ups a second consumed most of the box.

Accounts are now provisioned up front (`seed.mjs --users N`) and their tokens shared across VUs
via `SharedArray`. Identical scenario, same hardware, same 75 seconds:

| Ticket drop, 200 VUs | throughput | seat-lock p95 | queue-join p95 | thresholds |
|---|---|---|---|---|
| Registering per iteration | 214 req/s | 1.62 s | 1.92 s | ✗ |
| Pre-provisioned accounts | **1,353 req/s** | **258 ms** | **178 ms** | ✓ |

**6.3× throughput**, from moving one thing out of the measured window.

Both variants are kept — `-e FRESH_USERS=true` restores the original behaviour — because they
answer different questions. The first measures the true first-visit funnel including sign-up; the
second isolates the booking machinery. The pooled run's `waiting_room_admission_ms` is
correspondingly less meaningful, since a reused account is already admitted from its previous
iteration.

---

## Reading the results yourself

`loadtest/results/` holds the k6 JSON summaries and the console output:

```
results/browse.{json,txt}                  read-path baseline
results/ticket-drop.{json,txt}             full journey, pooled accounts
results/ticket-drop-fresh-users.{json,txt} full journey, registering per iteration
results/seat-contention.{json,txt}         150 buyers, one seat
results/before/                            the pre-fix runs quoted above
```

While a run is in progress, the application's own view of it is on
`/actuator/prometheus`. After the runs above:

```
tickify_seat_lock_total{result="acquired"}   8952
tickify_seat_lock_total{result="contended"}  3813
tickify_queue_joined_total                   7108
tickify_queue_promoted_total                 6885
tickify_booking_total{state="confirmed"}     3489
tickify_booking_total{state="failed"}         383
```

Contention rate, admission throughput and the payment outcome split — the three things that
actually tell you whether a drop is healthy. See [OBSERVABILITY.md](OBSERVABILITY.md).

---

## Tuning knobs

The levers worth turning during a drop, all configurable:

| Setting | Default | Effect |
|---|---|---|
| `QUEUE_BATCH_SIZE` | 50 | Users admitted per tick. Higher = shorter wait, more seat contention. |
| `QUEUE_PROMOTION_INTERVAL_MS` | 1000 | How often the promoter runs. |
| `QUEUE_SLOT_TTL` | 7m | How long an admitted shopper keeps the seat map. |
| `SEAT_LOCK_TTL` | 10m | How long a selected seat is held before payment settles. |
| `DB_POOL_SIZE` | 30 | Hikari pool. |
| `RABBITMQ_CONSUMERS` | 8 | Saga consumer concurrency. |
| `tickify.payment.simulated-latency-ms` | 3000 | Stand-in provider latency. |

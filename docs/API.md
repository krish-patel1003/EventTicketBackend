# API reference

Base URL `http://localhost:8080`. Interactive docs at
[`/swagger-ui.html`](http://localhost:8080/swagger-ui.html); the OpenAPI document is at
`/v3/api-docs`.

Authenticate with `POST /api/v1/auth/login` and send the access token on every other call:

```
Authorization: Bearer <accessToken>
```

---

## The booking flow

The order matters — each step is gated by the one before it.

```bash
BASE=http://localhost:8080

# 1. Register and sign in
curl -sX POST $BASE/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Passw0rd!","requestedRoles":["USER"]}'

TOKEN=$(curl -sX POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Passw0rd!"}' | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"

# 2. Join the waiting room  ->  {"position": 1247, "active": false}
curl -sX POST $BASE/api/v1/booking/queue/$EVENT_ID/join -H "$AUTH"

# 3. Poll until admitted    ->  {"position": -1, "active": true}
curl -s $BASE/api/v1/booking/queue/$EVENT_ID/status -H "$AUTH"

# 4. Load the seat map (free seats only)
curl -s $BASE/api/v1/events/$EVENT_ID/seats/available -H "$AUTH"

# 5. Lock seats and open a booking   ->  409 if someone beat you to one
curl -sX POST $BASE/api/v1/bookings/lock -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"$EVENT_ID\",\"seatIds\":[\"$SEAT_ID\"],\"ticketTypeId\":\"$TIER_ID\"}"

# 6. Start payment — returns immediately, does not wait for the provider
curl -sX POST $BASE/api/v1/bookings/payment/initiate -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"bookingId\":\"$BOOKING_ID\"}"

# 7. Poll until paymentStatus is SUCCESS (qrCode populated) or FAILED (seats released)
curl -s $BASE/api/v1/bookings/$BOOKING_ID -H "$AUTH"
```

---

## Auth & registration

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | Register. `requestedRoles` accepts `USER`, `ORGANIZER`, `STAFF`; `ADMIN` is stripped. |
| POST | `/api/v1/auth/login` | Public | Returns `{accessToken, refreshToken}`. |
| POST | `/api/v1/auth/refresh-token?refreshToken=…` | Public | New access token; re-reads roles, so a grant since login takes effect. |
| POST | `/api/v1/auth/logout?refreshToken=…` | Public | Revokes the refresh token. |
| GET | `/api/v1/user/me` | Authenticated | Current profile and roles. |

### E-mail verification

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/auth/email/resend-verification?email=…` | Public | Re-send the verification link. |
| GET | `/api/v1/auth/email/verify?uid=…&t=…` | Public | Verify an address. |

Set `EMAIL_VERIFICATION_REQUIRED=false` to disable the wall (the `loadtest` and
`integration-test` profiles do).

---

## Events

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/events/` | ORGANIZER, ADMIN | Create an event. Copies the venue's seat layout into `event_seats`. |
| GET | `/api/v1/events/?page=0&size=100` | Authenticated | Paged list, with ticket tiers. |
| PATCH | `/api/v1/events/{id}` | ORGANIZER, ADMIN | Update. |
| DELETE | `/api/v1/events/{id}` | ORGANIZER, ADMIN | Delete. |
| GET | `/api/v1/events/{id}/seats/available` | Authenticated | Seats that are neither reserved nor locked. |

<details>
<summary><code>POST /api/v1/events/</code> request</summary>

```json
{
  "title": "Aurora Live",
  "description": "One night only",
  "venue_id": "de2ced4a-885b-4bde-99a7-6aa9e0fdaf85",
  "startDate": "2030-01-01T20:00:00Z",
  "endDate": "2030-01-01T23:00:00Z",
  "ticketSaleStartDate": "2029-01-01T00:00:00Z",
  "ticketSaleEndDate": "2030-01-01T19:00:00Z"
}
```
</details>

## Ticket types

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/ticket-types/` | ORGANIZER, ADMIN | Create a price tier. `price` is a decimal. |
| GET | `/api/v1/ticket-types/event/{eventId}` | Authenticated | Tiers for an event. |
| PATCH | `/api/v1/ticket-types/{id}` | ORGANIZER, ADMIN | Update. |
| DELETE | `/api/v1/ticket-types/{id}` | ORGANIZER, ADMIN | Delete. |

## Event seats

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/event-seats/assign-ticket-type` | Authenticated | Assign a tier to seats. Omit `section`, `rowLabel` and `seatId` to apply it to every seat. |

A seat cannot be sold until it has a ticket type.

## Waiting room

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/booking/queue/{eventId}/join` | USER | Join. Idempotent — re-joining keeps your position. |
| GET | `/api/v1/booking/queue/{eventId}/status` | USER | Poll. `active: true` means you may lock seats. |

`position` is 1-based while queued, and `-1` once admitted (you are no longer in the sorted set).

## Bookings

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/bookings/lock` | USER | Lock seats all-or-nothing and open a `PENDING` booking. Requires an active slot. |
| POST | `/api/v1/bookings/payment/initiate` | USER | Publish `payment.requested`; returns at once. |
| GET | `/api/v1/bookings/{id}` | USER | One booking, with its QR code once confirmed. Poll this. |
| GET | `/api/v1/bookings/my-bookings` | USER | All of the caller's bookings. |

## Staff

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/staff/qr` | STAFF | Validate by QR payload. Single use. |
| POST | `/api/v1/staff/booking` | STAFF | Validate by booking reference. Single use. |

Both return `{"Valid": true|false}` and burn the ticket on success. A second presentation of the
same ticket returns `false`.

## Venues

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/api/v1/venue/` | Authenticated | All venues, with `seatCount`. |

## Operations

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/actuator/health` | Public | Aggregate health, with components. |
| GET | `/actuator/prometheus` | Public | Metrics. |
| GET | `/actuator/loggers` | Authenticated | Inspect and change log levels at runtime. |

---

## Status codes

The distinctions matter — the frontend and the load tests both branch on them.

| Code | When |
|---|---|
| `200` / `201` | Success. |
| `400` | Validation failure, or an unparseable path/query parameter. |
| `401` | Missing, malformed or expired access token. |
| `403` | Role not held, **or the waiting-room slot has expired**, or the booking is not yours. |
| `404` | No such event, booking or user. |
| **`409`** | **Seat contention** — somebody else locked or bought that seat first. Expected and frequent during a drop, and not an error. |
| `500` | A genuine fault. Under load this should be zero; the load tests assert it. |

All errors share one body shape:

```json
{
  "timestamp": "2026-09-02T18:18:33.630Z",
  "status": 409,
  "error": "Conflict",
  "message": "Seat already locked: 4725e1ec-0c49-4221-bd50-fa98fef43bae",
  "path": "/api/v1/bookings/lock"
}
```

The `X-Correlation-Id` response header ties the failure to its log lines — see
[OBSERVABILITY.md](OBSERVABILITY.md).

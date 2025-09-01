# API Design – Phase 1

## Auth & Registration
| Method | Endpoint                   | Role   | Description                  |
| ------ | -------------------------- | ------ | ---------------------------- |
| POST   | `/api/v1/auth/register`    | Public | Register new user            |
| POST   | `/api/v1/auth/login`       | Public | Login and get JWT            |
| POST   | `/api/v1/auth/logout`      | USER   | Logout + revoke refresh token|
| POST   | `/api/v1/auth/refresh-token` | Public | Refresh JWT using token      |
| GET    | `/api/v1/user/me`          | USER   | Get current user profile     |

### Email Verification
| Method | Endpoint                                | Role   | Description                 |
| ------ | --------------------------------------- | ------ | --------------------------- |
| POST   | `/api/v1/auth/email/resend-verification?email=...` | Public | Resend verification link    |
| GET    | `/api/v1/auth/email/verify?uid=...&t=...` | Public | Verify email address        |

---

## Event Service
| Method | Endpoint                 | Role                   | Description               |
| ------ | ------------------------ | ---------------------- | ------------------------- |
| POST   | `/api/v1/events/`        | ORGANIZER/ADMIN        | Create new event          |
| GET    | `/api/v1/events/`        | USER/ORGANIZER/ADMIN   | List active events        |
| PATCH  | `/api/v1/events/{id}`    | ORGANIZER/ADMIN        | Update event              |
| DELETE | `/api/v1/events/{id}`    | ORGANIZER/ADMIN        | Delete event              |

---

## Ticket Type Service
| Method | Endpoint                       | Role                   | Description           |
| ------ | ------------------------------ | ---------------------- | --------------------- |
| POST   | `/api/v1/ticket-types/`        | ORGANIZER/ADMIN        | Create ticket type    |
| GET    | `/api/v1/ticket-types/event/{eventId}` | USER/ORGANIZER/ADMIN   | Get ticket types      |
| PATCH  | `/api/v1/ticket-types/{id}`    | ORGANIZER/ADMIN        | Update ticket type    |
| DELETE | `/api/v1/ticket-types/{id}`    | ORGANIZER/ADMIN        | Delete ticket type    |

---

## Booking Service
| Method | Endpoint                           | Role | Description                           |
| ------ | ---------------------------------- | ---- | ------------------------------------- |
| POST   | `/api/v1/bookings/lock`            | USER | Lock seats (Redis) & create pending booking |
| POST   | `/api/v1/bookings/payment/initiate`| USER | Initiate payment (publishes to RabbitMQ) |
| POST   | `/api/v1/bookings/my-bookings`     | USER | View user’s bookings                  |

### Queueing
| Method | Endpoint                             | Role | Description                   |
| ------ | ------------------------------------ | ---- | ----------------------------- |
| POST   | `/api/v1/booking/queue/{eventId}/join`   | USER | Join booking queue for event  |
| GET    | `/api/v1/booking/queue/{eventId}/status` | USER | Get queue position + status   |

---

## Staff Service
| Method | Endpoint              | Role      | Description                         |
| ------ | --------------------- | --------- | ----------------------------------- |
| POST   | `/api/v1/staff/qr`    | STAFF     | Validate ticket by QR code          |
| POST   | `/api/v1/staff/booking` | STAFF   | Validate ticket by booking reference|

---

## Venue Service
| Method | Endpoint            | Role   | Description          |
| ------ | ------------------- | ------ | -------------------- |
| GET    | `/api/v1/venue/`    | Public | List all venues      |

---

## RabbitMQ Flow
1. **`/bookings/lock`** → create pending booking in DB
2. **`/bookings/payment/initiate`** → publish `payment.requested`
3. **Payment Service** consumes → publishes `payment.completed`
4. **Booking Service** consumes `payment.completed`
    - On SUCCESS → mark seats reserved, generate QR, publish `booking.confirmed`
    - On FAIL → release seat locks
5. **Notification Service** consumes `booking.confirmed` and sends email

---

## Redis Lock Structure
- Key: event:{eventId}:seat:{seatId}
- Value: userId (or lock reference)
- TTL: 10 mins

- Ensures no double-booking during high concurrency.
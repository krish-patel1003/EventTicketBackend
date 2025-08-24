# API Design – Phase 1

## Auth Endpoints (User Service)
| Method | Endpoint             | Role       | Description       |
| ------ | -------------------- | ---------- | ----------------- |
| POST   | `/api/auth/register` | Public     | Register new user |
| POST   | `/api/auth/login`    | Public     | Login and get JWT |
| GET    | `/api/users/me`      | USER/ADMIN | Profile info      |


## Event Endpoints (Event Service)
| Method | Endpoint                 | Role   | Description           |
| ------ | ------------------------ | ------ | --------------------- |
| POST   | `/api/events`            | ADMIN  | Create new event      |
| GET    | `/api/events`            | Public | List all events       |
| GET    | `/api/events/{event_id}`       | Public | Get single event      |
| POST   | `/api/events/{event_id}/seats` | ADMIN  | Define seating layout |
| POST   | `/api/events/{event_id}/ticket-types` | ADMIN  | Create ticket types |
| GET    | `/api/events/{event_id}/ticket-types`       | Public | Get ticket Types     |


## Booking Endpoints (Booking Service)

| Method | Endpoint                | Role | Description                             |
| ------ | ----------------------- | ---- | --------------------------------------- |
| POST   | `/api/bookings/lock`    | USER | Lock a seat (Redis)                     |
| POST   | `/api/bookings/confirm` | USER | Confirm booking (DB + emit to RabbitMQ) |
| GET    | `/api/bookings/my`      | USER | View user bookings                      |


## Email (Notification Service)
    - RabbitMQ queue: booking.confirmed
    - Consumer sends email via MailHog using JavaMailSender

## Redis Lock Structure 
```
Key: event:{eventId}:ticketType:{ticketTypeId}
Value: number of locked tickets
TTL: 10 mins
```
- Helps prevent overselling when a million people rush to book.

## Folder Structure
``` 
com.event-ticketing-backend-v1
├── user/
├── event/
├── booking/
│   └── service/
│       ├── BookingService.java       (business logic)
│       ├── TicketLockService.java   (Redis logic)
│       └── BookingPublisher.java    (publishes to RabbitMQ)
├── notification/
│   └── service/
│       └── BookingNotificationListener.java (RabbitMQ listener)
├── config/
│   ├── RabbitMQConfig.java
│   └── RedisConfig.java

```

# Database Design
## User Service

## Event Service
```
Events (
id UUID primary key,
event_title String,
event_description String,
ticket_sale_start_date,
ticket_sale_end_date,
event_start_date,
event_end_date,
organizer_id foreign key (user_id),
created_at,
updated_at
is_active boolean
)
```
```
Ticket-types (
id UUID primary key,
event_id foreign key (event_id),
type_title String,
type_Desc String,
price double,
no_of_seats_available,
)
```

## Booking service
```
bookings (
id UUID primary key,
booking_refrence generated_id
user_id (user who bought the ticket),
event_id (to which event)
ticket_type_id,
payment_Status,
billing_amount,
created_at
)
```

```
qr_code (
id UUID primary Key,
booking_id foreign key,
qr_code,
is_valid boolean,
used boolean,
method enum[scan, booking number],
created_at
)
```

## Notification Service

## Staff service

- staff should be able to scan a qr_code, and verify entry
- if qr_code doesn't work, enter booking_reference manually 
- easy interface to understand.
- only backend service for now

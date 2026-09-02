-- The JPA `BaseEntity` superclass stamps created_at/updated_at on every entity it backs,
-- but booking_seats was created in V002 without them. Reconcile the two so the
-- application can boot with `spring.jpa.hibernate.ddl-auto=validate` (schema drift = build failure).
ALTER TABLE booking_seats
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- booking_seats.seat_id already carries a UNIQUE constraint, which is what actually
-- prevents two confirmed bookings from claiming the same seat. Add the lookup index
-- used when loading a booking with its seats.
CREATE INDEX IF NOT EXISTS idx_booking_seats_booking_id ON booking_seats(booking_id);

-- The hot query on the seat-selection screen:
--   "give me every free seat for this event".
CREATE INDEX IF NOT EXISTS idx_event_seats_availability
    ON event_seats(event_id) WHERE is_locked = FALSE AND is_reserved = FALSE;

-- Staff gate scanning looks tickets up by their human-readable reference.
CREATE INDEX IF NOT EXISTS idx_bookings_reference ON bookings(booking_reference);

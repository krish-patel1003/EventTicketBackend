-- A seat may be claimed by many bookings over its lifetime, but by only one *live* claim.
--
-- V002 enforced that with a plain UNIQUE(seat_id), which conflates the two: once a booking
-- had touched a seat, no later booking could ever claim it. A declined payment released the
-- Redis lock and put the seat back on sale, but its booking_seats row stayed behind, so the
-- next buyer hit `duplicate key value violates unique constraint "booking_seats_seat_id_key"`
-- and a 500. The seat was effectively unsellable for the rest of the event.
--
-- Replacing it with a partial unique index keeps the guarantee that matters — no two live
-- claims on one seat — while letting a released claim stay in the record for history.

ALTER TABLE booking_seats
    ADD COLUMN IF NOT EXISTS released_at TIMESTAMP NULL;

ALTER TABLE booking_seats
    DROP CONSTRAINT IF EXISTS booking_seats_seat_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_booking_seats_live_claim
    ON booking_seats (seat_id)
    WHERE released_at IS NULL;

-- Releasing a seat is driven by the state of its booking, so this is the supporting index
-- for the reaper that sweeps up abandoned checkouts.
CREATE INDEX IF NOT EXISTS idx_bookings_status_created
    ON bookings (payment_status, created_at);

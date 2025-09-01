-- ========== VENUE & LAYOUT ==========
CREATE TABLE venues (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    location TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE venue_seats (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
    seat_number VARCHAR(10) NOT NULL,
    row_label VARCHAR(10),
    section VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (venue_id, seat_number)
);

CREATE INDEX idx_venue_seats_venue_id ON venue_seats(venue_id);

-- ========== EVENTS ==========
CREATE TABLE events (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    ticket_sale_start_date TIMESTAMP,
    ticket_sale_end_date TIMESTAMP,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    organizer_id UUID REFERENCES users(id) ON DELETE SET NULL,
    venue_id UUID NOT NULL REFERENCES venues(id),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_events_organizer ON events(organizer_id);
CREATE INDEX idx_events_venue_id ON events(venue_id);

-- ========== TICKET TYPES ==========
CREATE TABLE ticket_types (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    total_quantity INT NOT NULL,
    available_quantity INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ticket_types_event_id ON ticket_types(event_id);

-- ========== EVENT SEATS ==========
CREATE TABLE event_seats (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    seat_number VARCHAR(10) NOT NULL,
    row_label VARCHAR(10),
    section VARCHAR(50),
    ticket_type_id UUID REFERENCES ticket_types(id) ON DELETE SET NULL,
    is_locked BOOLEAN DEFAULT FALSE,
    is_reserved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (event_id, seat_number)
);

CREATE INDEX idx_event_seats_event_id ON event_seats(event_id);
CREATE INDEX idx_event_seats_ticket_type_id ON event_seats(ticket_type_id);

-- ========== BOOKINGS ==========
CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    booking_reference VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    ticket_type_id UUID NOT NULL REFERENCES ticket_types(id),
    event_seat_id UUID REFERENCES event_seats(id), -- optional if assigned seat
    payment_status VARCHAR(20) NOT NULL, -- e.g., PAID, FAILED, PENDING
    billing_amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bookings_user_id ON bookings(user_id);
CREATE INDEX idx_bookings_event_id ON bookings(event_id);
CREATE INDEX idx_bookings_ticket_type ON bookings(ticket_type_id);

-- ========== QR CODES ==========
CREATE TABLE qr_codes (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    qr_code TEXT NOT NULL,
    is_valid BOOLEAN DEFAULT TRUE,
    used BOOLEAN DEFAULT FALSE,
    method VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_qr_codes_booking_id ON qr_codes(booking_id);


CREATE TABLE booking_seats (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    seat_id UUID NOT NULL REFERENCES event_seats(id),
    UNIQUE(seat_id)  -- ensures no double booking of same seat
);
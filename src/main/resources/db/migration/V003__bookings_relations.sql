CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    booking_reference VARCHAR(20) UNIQUE,
    user_id UUID NOT NULL,
    event_id UUID NOT NULL,
    ticket_type_id NOT NULL,
    payment_status ENUM('PENDING', 'SUCCESS', 'FAILED') DEFAULT 'PENDING',
    billing_amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bookings_booking_id ON bookings(id);

CREATE TABLE qr_codes (
    id UUID PRIMARY KEY,
    booking_id UUID REFERENCES bookings(id),
    qr_code TEXT NOT NUll,
    is_valid BOOLEAN DEFAULT TRUE,
    used BOOLEAN DEFAULT FALSE,
    method ENUM('SCAN', 'BOOKING_REFERENCE') DEFAULT 'SCAN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
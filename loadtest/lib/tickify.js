import http from 'k6/http';
import { check } from 'k6';

// 409 means "somebody else got that seat first", which is the system working correctly.
// Left as a default 4xx it would be counted in http_req_failed and make a healthy run
// look like a broken one.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 409));

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/** Shared JSON headers, plus a bearer token when one is supplied. */
export function headers(token) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

export function register(email, password, roles) {
  return http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ email, password, requestedRoles: roles }),
    { headers: headers(), tags: { name: 'register' } },
  );
}

export function login(email, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: headers(), tags: { name: 'login' } },
  );
  check(res, { 'login 200': (r) => r.status === 200 });
  return res.status === 200 ? res.json('accessToken') : null;
}

/**
 * Registers a throwaway account and returns its access token.
 *
 * Each virtual user needs its own identity: the waiting room is keyed by user, so sharing
 * one account across VUs would collapse thousands of shoppers into a single queue entry
 * and measure nothing.
 */
export function newUserToken(prefix) {
  const email = `${prefix}-${__VU}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@load.tickify.test`;
  const password = 'Passw0rd!';
  register(email, password, ['USER']);
  return login(email, password);
}

export function joinQueue(token, eventId) {
  return http.post(`${BASE_URL}/api/v1/booking/queue/${eventId}/join`, null, {
    headers: headers(token),
    tags: { name: 'queue_join' },
  });
}

export function queueStatus(token, eventId) {
  return http.get(`${BASE_URL}/api/v1/booking/queue/${eventId}/status`, {
    headers: headers(token),
    tags: { name: 'queue_status' },
  });
}

export function availableSeats(token, eventId) {
  return http.get(`${BASE_URL}/api/v1/events/${eventId}/seats/available`, {
    headers: headers(token),
    tags: { name: 'seats_available' },
  });
}

/**
 * Attempts to lock seats.
 *
 * 409 is an expected, correct outcome under contention — somebody else got there first —
 * so the caller decides how to count it rather than k6 flagging it as a failure.
 */
export function lockSeats(token, eventId, seatIds, ticketTypeId) {
  return http.post(
    `${BASE_URL}/api/v1/bookings/lock`,
    JSON.stringify({ eventId, seatIds, ticketTypeId }),
    { headers: headers(token), tags: { name: 'seat_lock' } },
  );
}

export function initiatePayment(token, bookingId) {
  return http.post(
    `${BASE_URL}/api/v1/bookings/payment/initiate`,
    JSON.stringify({ bookingId }),
    { headers: headers(token), tags: { name: 'payment_initiate' } },
  );
}

export function getBooking(token, bookingId) {
  return http.get(`${BASE_URL}/api/v1/bookings/${bookingId}`, {
    headers: headers(token),
    tags: { name: 'booking_get' },
  });
}

/** Picks `count` random seats from the available list, to spread contention realistically. */
export function pickSeats(seats, count) {
  const chosen = [];
  const pool = seats.length;
  if (pool === 0) return chosen;

  for (let i = 0; i < count; i++) {
    chosen.push(seats[Math.floor(Math.random() * pool)].id);
  }
  return [...new Set(chosen)];
}

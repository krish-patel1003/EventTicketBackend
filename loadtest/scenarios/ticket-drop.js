import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
  availableSeats,
  getBooking,
  initiatePayment,
  joinQueue,
  lockSeats,
  newUserToken,
  pickSeats,
  queueStatus,
} from '../lib/tickify.js';

/**
 * Shopper accounts, provisioned by `loadtest/seed.mjs --users N` before the run.
 *
 * Registering during the test measured the wrong thing: bcrypt is deliberately expensive,
 * and signing up a few hundred users a second ate most of the CPU, inflating every latency
 * on the page. SharedArray keeps one copy of the tokens across all VUs.
 */
const users = new SharedArray('shoppers', () => {
  if (__ENV.FRESH_USERS === 'true') {
    return []; // opt back in to signing up inside the test, to measure that funnel instead
  }
  try {
    return JSON.parse(open(__ENV.USERS_FILE || '../results/users.json'));
  } catch {
    return []; // no pool provisioned; fall back to registering per iteration
  }
});

/**
 * The scenario the whole system exists for: a ticket drop.
 *
 * Every virtual user arrives at once for the same event, queues, waits to be admitted,
 * picks seats at random from what is left, and pays. Seats are chosen randomly precisely so
 * that users collide — the interesting number is not how fast a lock succeeds but whether
 * the losers get a clean 409 instead of a 500, a timeout, or a double sale.
 *
 * Run:
 *   k6 run -e EVENT_ID=... -e TICKET_TYPE_ID=... loadtest/scenarios/ticket-drop.js
 */

const seatsPerUser = Number(__ENV.SEATS_PER_USER || 2);
const eventId = __ENV.EVENT_ID;
const ticketTypeId = __ENV.TICKET_TYPE_ID;

// --- custom metrics ------------------------------------------------------------------
const admissionTime = new Trend('waiting_room_admission_ms', true);
const bookingConfirmTime = new Trend('booking_confirm_ms', true);
const seatsWon = new Counter('seats_won');
const seatContention = new Counter('seat_contention_409');
const unexpectedErrors = new Counter('unexpected_errors');
const bookingSuccess = new Rate('booking_success_rate');
const admitted = new Rate('admitted_rate');

export const options = {
  scenarios: {
    drop: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: Number(__ENV.PEAK_VUS || 200) }, // doors open
        { duration: '45s', target: Number(__ENV.PEAK_VUS || 200) }, // sustained rush
        { duration: '15s', target: 0 },                              // drain
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // A losing shopper must still get a fast, correct answer.
    'http_req_failed{name:seat_lock}': ['rate<0.01'],
    'http_req_duration{name:seat_lock}': ['p(95)<1000'],
    'http_req_duration{name:queue_join}': ['p(95)<500'],
    'http_req_duration{name:queue_status}': ['p(95)<300'],
    'http_req_duration{name:seats_available}': ['p(95)<2000'],
    unexpected_errors: ['count<1'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  if (!eventId || !ticketTypeId) {
    throw new Error('EVENT_ID and TICKET_TYPE_ID are required (see loadtest/seed.mjs)');
  }
  return { eventId, ticketTypeId };
}

export default function (data) {
  // Spread VUs across the pool so each shopper queues as a distinct user; the waiting room
  // is keyed by user, so reusing one account would collapse the queue to a single entry.
  const token = users.length
    ? users[(__VU * 7919 + __ITER) % users.length]
    : newUserToken('drop');

  if (!token) {
    unexpectedErrors.add(1);
    return;
  }

  // --- 1. join the waiting room -------------------------------------------------------
  const join = joinQueue(token, data.eventId);
  if (!check(join, { 'queue join 200': (r) => r.status === 200 })) {
    unexpectedErrors.add(1);
    return;
  }

  // --- 2. wait for admission ----------------------------------------------------------
  const queuedAt = Date.now();
  let isAdmitted = join.json('active') === true;
  for (let attempt = 0; attempt < 40 && !isAdmitted; attempt++) {
    sleep(0.5);
    const status = queueStatus(token, data.eventId);
    if (status.status !== 200) {
      unexpectedErrors.add(1);
      return;
    }
    isAdmitted = status.json('active') === true;
  }

  admitted.add(isAdmitted);
  if (!isAdmitted) return; // still queued when the test ended; not an error
  admissionTime.add(Date.now() - queuedAt);

  // --- 3. choose seats ----------------------------------------------------------------
  const seatsResponse = availableSeats(token, data.eventId);
  if (!check(seatsResponse, { 'seat map 200': (r) => r.status === 200 })) {
    unexpectedErrors.add(1);
    return;
  }

  const seats = seatsResponse.json();
  const wanted = pickSeats(seats, seatsPerUser);
  if (wanted.length === 0) return; // sold out, which is a legitimate end state

  // --- 4. lock ------------------------------------------------------------------------
  const lock = lockSeats(token, data.eventId, wanted, data.ticketTypeId);

  if (lock.status === 409) {
    // Someone else won the race. This is the system behaving correctly under contention.
    seatContention.add(1);
    bookingSuccess.add(false);
    return;
  }
  if (lock.status !== 200) {
    // 403 means the admission slot expired mid-checkout, which is also legitimate.
    if (lock.status !== 403) unexpectedErrors.add(1);
    bookingSuccess.add(false);
    return;
  }

  seatsWon.add(wanted.length);
  const bookingId = lock.json('bookingId');

  // --- 5. pay -------------------------------------------------------------------------
  const payment = initiatePayment(token, bookingId);
  if (!check(payment, { 'payment accepted': (r) => r.status === 200 })) {
    unexpectedErrors.add(1);
    bookingSuccess.add(false);
    return;
  }

  // --- 6. poll until the saga settles --------------------------------------------------
  const paidAt = Date.now();
  for (let attempt = 0; attempt < 30; attempt++) {
    sleep(0.5);
    const booking = getBooking(token, bookingId);
    if (booking.status !== 200) {
      unexpectedErrors.add(1);
      break;
    }
    const state = booking.json('paymentStatus');
    if (state === 'SUCCESS') {
      bookingConfirmTime.add(Date.now() - paidAt);
      bookingSuccess.add(true);
      // A confirmed booking must carry its ticket.
      check(booking, { 'confirmed booking has a QR code': (r) => !!r.json('qrCode') });
      return;
    }
    if (state === 'FAILED') {
      // A declined card is a correct outcome of the saga, not a load-test failure.
      bookingSuccess.add(false);
      return;
    }
  }
}

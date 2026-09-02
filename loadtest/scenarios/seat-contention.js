import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { availableSeats, joinQueue, lockSeats, newUserToken, queueStatus } from '../lib/tickify.js';
import { sleep } from 'k6';

/**
 * Correctness under contention, not throughput.
 *
 * Every virtual user targets the *same* single seat at the same moment. Exactly one may win;
 * everyone else must be told 409. The assertion is the threshold `seat_won == 1` at the
 * bottom of this file — if the Redis lock were replaced by a read-then-write, or by an
 * optimistic database check, this run would oversell and the threshold would fail.
 *
 * Run:
 *   k6 run -e EVENT_ID=... -e TICKET_TYPE_ID=... -e SEAT_ID=... loadtest/scenarios/seat-contention.js
 */

const seatWon = new Counter('seat_won');
const seatRefused = new Counter('seat_refused');
const unexpected = new Counter('unexpected_status');

export const options = {
  scenarios: {
    stampede: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 100),
      iterations: Number(__ENV.VUS || 100), // exactly one attempt per user
      maxDuration: '2m',
    },
  },
  thresholds: {
    // The whole point: one seat, one winner, no matter how many people ask at once.
    seat_won: ['count==1'],
    unexpected_status: ['count<1'],
  },
};

export function setup() {
  if (!__ENV.EVENT_ID || !__ENV.TICKET_TYPE_ID) {
    throw new Error('EVENT_ID and TICKET_TYPE_ID are required (see loadtest/seed.mjs)');
  }

  // Pin one seat for everyone to fight over. Chosen here, in setup, so every VU targets
  // the identical seat rather than each picking its own.
  const token = newUserToken('contention-setup');
  joinQueue(token, __ENV.EVENT_ID);
  for (let i = 0; i < 20; i++) {
    if (queueStatus(token, __ENV.EVENT_ID).json('active') === true) break;
    sleep(0.5);
  }

  const seats = availableSeats(token, __ENV.EVENT_ID).json();
  if (!seats || seats.length === 0) throw new Error('No available seats to contend for');

  const seatId = __ENV.SEAT_ID || seats[0].id;
  return { eventId: __ENV.EVENT_ID, ticketTypeId: __ENV.TICKET_TYPE_ID, seatId };
}

export default function (data) {
  const token = newUserToken('contention');
  if (!token) {
    unexpected.add(1);
    return;
  }

  joinQueue(token, data.eventId);

  // Wait for admission; without a slot the lock is refused at the gate with 403 and the
  // seat is never actually contended for.
  let isAdmitted = false;
  for (let i = 0; i < 40 && !isAdmitted; i++) {
    sleep(0.5);
    isAdmitted = queueStatus(token, data.eventId).json('active') === true;
  }
  if (!isAdmitted) return;

  const response = lockSeats(token, data.eventId, [data.seatId], data.ticketTypeId);

  if (response.status === 200) {
    seatWon.add(1);
  } else if (response.status === 409) {
    seatRefused.add(1);
  } else if (response.status !== 403) {
    unexpected.add(1);
  }

  check(response, {
    'lock answered 200 or 409': (r) => r.status === 200 || r.status === 409 || r.status === 403,
  });
}

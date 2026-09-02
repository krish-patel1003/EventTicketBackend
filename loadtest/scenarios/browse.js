import { check, sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL, headers, newUserToken } from '../lib/tickify.js';

/**
 * Read-path baseline.
 *
 * Browsing is what most traffic actually is, even during a drop, and it is the number to
 * compare the booking path against: if listing events is already slow, nothing measured on
 * the write path means much.
 *
 * Run:  k6 run loadtest/scenarios/browse.js
 */
export const options = {
  scenarios: {
    browsing: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 100),
      duration: __ENV.DURATION || '45s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:events_list}': ['p(95)<400', 'p(99)<800'],
    'http_req_duration{name:me}': ['p(95)<300'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  // One account, reused: this scenario measures read latency, not registration throughput.
  return { token: newUserToken('browse') };
}

export default function (data) {
  const profile = http.get(`${BASE_URL}/api/v1/user/me`, {
    headers: headers(data.token),
    tags: { name: 'me' },
  });
  check(profile, { 'profile 200': (r) => r.status === 200 });

  const events = http.get(`${BASE_URL}/api/v1/events/?page=0&size=20`, {
    headers: headers(data.token),
    tags: { name: 'events_list' },
  });
  check(events, { 'events 200': (r) => r.status === 200 });

  const venues = http.get(`${BASE_URL}/api/v1/venue/`, {
    headers: headers(data.token),
    tags: { name: 'venues_list' },
  });
  check(venues, { 'venues 200': (r) => r.status === 200 });

  sleep(0.5); // think time, so 100 VUs model 100 shoppers rather than 100 tight loops
}

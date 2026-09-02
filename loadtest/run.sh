#!/usr/bin/env bash
#
# Seeds an event and runs the k6 scenarios against a running Tickify instance.
#
#   ./loadtest/run.sh              # all scenarios
#   ./loadtest/run.sh ticket-drop  # one scenario
#
# Results land in loadtest/results/ as k6 JSON summaries.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULTS="$ROOT/loadtest/results"
mkdir -p "$RESULTS"

command -v k6 >/dev/null || { echo "k6 is not installed: https://k6.io/docs/get-started/installation/" >&2; exit 1; }
curl -sf "$BASE_URL/actuator/health" >/dev/null || { echo "Tickify is not answering on $BASE_URL" >&2; exit 1; }

echo "==> Seeding a load-test event"
eval "$(BASE_URL="$BASE_URL" node "$ROOT/loadtest/seed.mjs")"
echo "    event=$EVENT_ID seats=$AVAILABLE_SEATS"

run_scenario() {
  local name="$1"; shift
  echo ""
  echo "==> $name"
  k6 run \
    --summary-export "$RESULTS/$name.json" \
    -e BASE_URL="$BASE_URL" \
    -e EVENT_ID="$EVENT_ID" \
    -e TICKET_TYPE_ID="$TICKET_TYPE_ID" \
    -e SEAT_ID="$SEAT_ID" \
    "$@" \
    "$ROOT/loadtest/scenarios/$name.js" 2>&1 | tee "$RESULTS/$name.txt"
}

only="${1:-}"
if [ -n "$only" ]; then
  run_scenario "$only"
else
  run_scenario browse
  # A fresh event per scenario: ticket-drop consumes seats, and seat-contention needs a
  # seat nobody has taken yet.
  run_scenario ticket-drop
  eval "$(BASE_URL="$BASE_URL" node "$ROOT/loadtest/seed.mjs")"
  run_scenario seat-contention
fi

echo ""
echo "==> Summaries written to $RESULTS"

#!/usr/bin/env bash
#
# Starts / stops the packaged Tickify jar against an already-running backing stack
# (see scripts/dev-stack.sh). Keeps a PID file so stopping does not rely on pattern
# matching process command lines.
#
# Usage:  ./scripts/run-app.sh start [--profile loadtest] | stop | status | logs
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/target/tickify-1.0.0.jar"
PID_FILE="${TICKIFY_PID_FILE:-$ROOT/target/tickify.pid}"
LOG_FILE="${TICKIFY_APP_LOG:-$ROOT/target/tickify-app.log}"
PORT="${SERVER_PORT:-8080}"

case "${1:-start}" in
  start)
    shift || true
    [ -f "$JAR" ] || { echo "Build first: ./mvnw -DskipTests package" >&2; exit 1; }

    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "Already running (pid $(cat "$PID_FILE"))"; exit 0
    fi

    # setsid detaches the JVM from this shell's process group so it survives the
    # calling terminal, and stdin is closed so it never blocks waiting for input.
    setsid env DOCKER_COMPOSE_ENABLED=false java -jar "$JAR" "$@" \
      >"$LOG_FILE" 2>&1 </dev/null &
    echo $! >"$PID_FILE"

    for _ in $(seq 1 60); do
      if curl -sf "http://localhost:$PORT/actuator/health" >/dev/null 2>&1; then
        echo "Tickify up on http://localhost:$PORT (pid $(cat "$PID_FILE"))"; exit 0
      fi
      sleep 2
    done

    echo "Did not become healthy in time; last log lines:" >&2
    tail -30 "$LOG_FILE" >&2
    exit 1
    ;;
  stop)
    rm -f "$PID_FILE"

    # Reap every Tickify JVM, not just the recorded one: two live instances both consume
    # from the same RabbitMQ queues, so a forgotten process silently steals half the
    # messages and the symptom looks like a broken saga. Matched on the JVM's own cmdline
    # (never `pkill -f`, which would also match the shell running this script).
    tickify_pids() {
      for pid in $(pgrep -x java 2>/dev/null); do
        if tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -q "tickify-.*\.jar"; then
          echo "$pid"
        fi
      done
    }

    for pid in $(tickify_pids); do kill "$pid" 2>/dev/null || true; done

    # Wait for a graceful exit, then insist. Returning while the old JVM still holds the
    # port and the queues is what makes the next `start` look like it worked when it did not.
    for _ in $(seq 1 20); do
      [ -z "$(tickify_pids)" ] && break
      sleep 1
    done
    for pid in $(tickify_pids); do kill -9 "$pid" 2>/dev/null || true; done

    echo "Stopped"
    ;;
  status)
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "running (pid $(cat "$PID_FILE"))"
      curl -s "http://localhost:$PORT/actuator/health" || true
    else
      echo "not running"
    fi
    ;;
  logs)
    tail -f "$LOG_FILE"
    ;;
  *)
    echo "usage: $0 {start|stop|status|logs}" >&2; exit 1
    ;;
esac

#!/usr/bin/env bash
#
# Starts Postgres, Redis, RabbitMQ and an SMTP sink as native processes.
#
# The normal way to run Tickify's dependencies is `docker compose up` (see compose.yaml).
# This script is the fallback for environments where no Docker daemon is available -- CI
# runners without privileged mode, locked-down VMs, and the sandbox the load-test results
# in docs/LOAD_TESTING.md were produced on.
#
# Usage:  ./scripts/dev-stack.sh start | stop | status
set -euo pipefail

DATA_DIR="${TICKIFY_DATA_DIR:-/var/lib/tickify}"
LOG_DIR="${TICKIFY_LOG_DIR:-/var/log/tickify}"
PG_BIN="${PG_BIN:-/usr/lib/postgresql/16/bin}"
DB_NAME="${DB_NAME:-mydatabase}"
DB_USER="${DB_USER:-myuser}"
DB_PASSWORD="${DB_PASSWORD:-secret}"

log() { printf '\033[36m==>\033[0m %s\n' "$*"; }

start_postgres() {
  if su postgres -c "$PG_BIN/pg_isready -h 127.0.0.1 -q" 2>/dev/null; then
    log "Postgres already running"; return
  fi

  mkdir -p "$DATA_DIR/pgdata" "$LOG_DIR"
  chown -R postgres:postgres "$DATA_DIR" "$LOG_DIR"
  chmod 750 "$DATA_DIR/pgdata"

  if [ ! -f "$DATA_DIR/pgdata/PG_VERSION" ]; then
    log "Initialising Postgres cluster"
    su postgres -c "$PG_BIN/initdb -D $DATA_DIR/pgdata -U postgres --auth=trust" >"$LOG_DIR/initdb.log" 2>&1
  fi

  log "Starting Postgres on 5432"
  su postgres -c "$PG_BIN/pg_ctl -D $DATA_DIR/pgdata -l $LOG_DIR/pg.log \
      -o '-p 5432 -c listen_addresses=127.0.0.1 -c max_connections=300 -c shared_buffers=512MB' start"
  sleep 2

  su postgres -c "psql -h 127.0.0.1 -U postgres -tAc \"SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'\"" \
    | grep -q 1 || su postgres -c "psql -h 127.0.0.1 -U postgres -c \"CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD' SUPERUSER;\""
  su postgres -c "psql -h 127.0.0.1 -U postgres -tAc \"SELECT 1 FROM pg_database WHERE datname='$DB_NAME'\"" \
    | grep -q 1 || su postgres -c "psql -h 127.0.0.1 -U postgres -c 'CREATE DATABASE $DB_NAME OWNER $DB_USER;'"
}

start_redis() {
  if redis-cli ping >/dev/null 2>&1; then log "Redis already running"; return; fi
  log "Starting Redis on 6379"
  # Persistence off: every key Tickify puts in Redis (seat locks, queue slots) is
  # deliberately ephemeral, and fsync would distort the load-test latencies.
  redis-server --daemonize yes --port 6379 --save '' --appendonly no --maxmemory 512mb
}

start_rabbitmq() {
  if rabbitmqctl -n rabbit@localhost status >/dev/null 2>&1; then
    log "RabbitMQ already running"; return
  fi

  mkdir -p "$DATA_DIR/rabbit" "$LOG_DIR/rabbit"
  chown -R rabbitmq:rabbitmq "$DATA_DIR/rabbit" "$LOG_DIR/rabbit"

  log "Starting RabbitMQ on 5672"
  RABBITMQ_MNESIA_BASE="$DATA_DIR/rabbit" RABBITMQ_LOG_BASE="$LOG_DIR/rabbit" \
    RABBITMQ_NODENAME=rabbit@localhost nohup rabbitmq-server >"$LOG_DIR/rabbitmq-boot.log" 2>&1 &

  for _ in $(seq 1 30); do
    rabbitmqctl -n rabbit@localhost status >/dev/null 2>&1 && break
    sleep 4
  done

  rabbitmqctl -n rabbit@localhost list_users 2>/dev/null | grep -q "^$DB_USER" || {
    rabbitmqctl -n rabbit@localhost add_user "$DB_USER" "$DB_PASSWORD"
    rabbitmqctl -n rabbit@localhost set_user_tags "$DB_USER" administrator
    rabbitmqctl -n rabbit@localhost set_permissions -p / "$DB_USER" ".*" ".*" ".*"
  }
}

start_smtp() {
  if pgrep -f 'tickify-smtp-sink' >/dev/null 2>&1; then log "SMTP sink already running"; return; fi
  log "Starting SMTP sink on 1025"
  nohup python3 "$(dirname "$0")/smtp-sink.py" tickify-smtp-sink >"$LOG_DIR/smtp.log" 2>&1 &
}

case "${1:-start}" in
  start)
    start_postgres; start_redis; start_rabbitmq; start_smtp
    log "Stack ready: postgres:5432 redis:6379 rabbitmq:5672 smtp:1025"
    ;;
  stop)
    su postgres -c "$PG_BIN/pg_ctl -D $DATA_DIR/pgdata stop" 2>/dev/null || true
    redis-cli shutdown nosave 2>/dev/null || true
    rabbitmqctl -n rabbit@localhost shutdown 2>/dev/null || true
    pkill -f 'tickify-smtp-sink' 2>/dev/null || true
    log "Stack stopped"
    ;;
  status)
    su postgres -c "$PG_BIN/pg_isready -h 127.0.0.1" || true
    redis-cli ping 2>/dev/null || echo "redis: down"
    rabbitmqctl -n rabbit@localhost status >/dev/null 2>&1 && echo "rabbitmq: up" || echo "rabbitmq: down"
    ;;
  *)
    echo "usage: $0 {start|stop|status}" >&2; exit 1
    ;;
esac

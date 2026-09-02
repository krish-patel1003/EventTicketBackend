import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { bookings, events, queue, ticketTypes } from '../api/endpoints';
import type { Booking, EventSeat, EventSummary, QueueStatus, TicketType } from '../api/types';
import { SeatMap } from '../components/SeatMap';
import { Alert, Loading, PaymentBadge, Steps, formatDateTime, formatMoney } from '../components/common';

const MAX_SEATS_PER_ORDER = 6;
const QUEUE_POLL_MS = 2000;
const BOOKING_POLL_MS = 1500;

type Stage = 'queue' | 'seats' | 'paying' | 'done';

export function EventDetailPage() {
  const { eventId = '' } = useParams();

  const [event, setEvent] = useState<EventSummary | null>(null);
  const [tiers, setTiers] = useState<TicketType[]>([]);
  const [selectedTier, setSelectedTier] = useState<string>('');
  const [seats, setSeats] = useState<EventSeat[] | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const [queueStatus, setQueueStatus] = useState<QueueStatus | null>(null);
  const [initialPosition, setInitialPosition] = useState<number | null>(null);
  const [booking, setBooking] = useState<Booking | null>(null);

  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const admitted = queueStatus?.active ?? false;
  const stage: Stage = booking
    ? booking.paymentStatus === 'SUCCESS'
      ? 'done'
      : 'paying'
    : admitted
      ? 'seats'
      : 'queue';

  // --- event + ticket tiers ---------------------------------------------------------
  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const page = await events.list(0, 200);
        const found = page.content.find((e) => e.id === eventId) ?? null;
        if (cancelled) return;
        setEvent(found);

        const tierList = await ticketTypes.forEvent(eventId);
        if (cancelled) return;
        setTiers(tierList);
        setSelectedTier((current) => current || tierList[0]?.id || '');
      } catch (caught) {
        if (!cancelled) setError(caught instanceof Error ? caught.message : 'Failed to load event');
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [eventId]);

  // --- waiting room -----------------------------------------------------------------
  // Joining is idempotent server-side, so re-entering the page keeps the original place.
  useEffect(() => {
    let cancelled = false;
    let timer: number | undefined;

    (async () => {
      try {
        const joined = await queue.join(eventId);
        if (cancelled) return;
        setQueueStatus(joined);
        setInitialPosition(joined.position > 0 ? joined.position : 1);

        const poll = async () => {
          try {
            const status = await queue.status(eventId);
            if (cancelled) return;
            setQueueStatus(status);
            // Once admitted there is nothing left to watch; the slot's TTL takes over.
            if (!status.active) timer = window.setTimeout(poll, QUEUE_POLL_MS);
          } catch {
            if (!cancelled) timer = window.setTimeout(poll, QUEUE_POLL_MS);
          }
        };

        if (!joined.active) timer = window.setTimeout(poll, QUEUE_POLL_MS);
      } catch (caught) {
        if (!cancelled) setError(caught instanceof Error ? caught.message : 'Could not join the queue');
      }
    })();

    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [eventId]);

  // --- seat map, loaded once the user is admitted -----------------------------------
  const loadSeats = useCallback(async () => {
    try {
      setSeats(await events.availableSeats(eventId));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not load seats');
    }
  }, [eventId]);

  useEffect(() => {
    if (admitted && seats === null) void loadSeats();
  }, [admitted, seats, loadSeats]);

  // --- payment polling ---------------------------------------------------------------
  const pollTimer = useRef<number>();
  useEffect(() => {
    if (!booking || booking.paymentStatus !== 'PENDING') return;

    const tick = async () => {
      try {
        const latest = await bookings.get(booking.id);
        setBooking(latest);
        if (latest.paymentStatus === 'PENDING') {
          pollTimer.current = window.setTimeout(tick, BOOKING_POLL_MS);
        }
      } catch {
        pollTimer.current = window.setTimeout(tick, BOOKING_POLL_MS);
      }
    };

    pollTimer.current = window.setTimeout(tick, BOOKING_POLL_MS);
    return () => window.clearTimeout(pollTimer.current);
  }, [booking]);

  function toggleSeat(seatId: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(seatId)) next.delete(seatId);
      else if (next.size < MAX_SEATS_PER_ORDER) next.add(seatId);
      return next;
    });
  }

  async function lockAndPay() {
    setBusy(true);
    setError(null);
    setNotice(null);

    try {
      const locked = await bookings.lock(eventId, [...selected], selectedTier);
      await bookings.initiatePayment(locked.bookingId);
      setBooking(await bookings.get(locked.bookingId));
    } catch (caught) {
      if (caught instanceof ApiError && caught.isConflict) {
        // Somebody else won the race. Refresh the map so the user picks from what is left.
        setError('Someone just took one of those seats. The map has been refreshed — please pick again.');
        setSelected(new Set());
        await loadSeats();
      } else if (caught instanceof ApiError && caught.isForbidden) {
        setError('Your booking slot expired. Rejoin the queue to try again.');
        setQueueStatus((s) => (s ? { ...s, active: false } : s));
      } else {
        setError(caught instanceof Error ? caught.message : 'Booking failed');
      }
    } finally {
      setBusy(false);
    }
  }

  async function retryAfterDecline() {
    setBooking(null);
    setSelected(new Set());
    setNotice(null);
    await loadSeats();
  }

  if (error && !event) return <main className="page"><Alert kind="error">{error}</Alert></main>;
  if (!event) return <main className="page"><Loading what="event" /></main>;

  const tier = tiers.find((t) => t.id === selectedTier);
  const total = tier ? tier.price * selected.size : 0;
  const stepIndex = stage === 'queue' ? 0 : stage === 'seats' ? 1 : stage === 'paying' ? 2 : 3;

  return (
    <main className="page">
      <div className="page-header">
        <h1>{event.title}</h1>
        <p>
          {formatDateTime(event.startDate)}
          {event.description ? ` · ${event.description}` : ''}
        </p>
      </div>

      <Steps current={stepIndex as 0 | 1 | 2 | 3} />

      {error && <Alert kind="error">{error}</Alert>}
      {notice && <Alert kind="info">{notice}</Alert>}

      {stage === 'queue' && (
        <WaitingRoom status={queueStatus} initialPosition={initialPosition} />
      )}

      {stage === 'seats' && (
        <div className="card">
          <div className="spread" style={{ marginBottom: '1rem' }}>
            <div>
              <h2 style={{ marginBottom: '.15rem' }}>Choose your seats</h2>
              <p className="small muted" style={{ margin: 0 }}>
                You're in. Up to {MAX_SEATS_PER_ORDER} seats per order.
              </p>
            </div>
            <button className="secondary" onClick={() => void loadSeats()}>
              Refresh map
            </button>
          </div>

          {tiers.length > 0 && (
            <div className="field" style={{ maxWidth: 340 }}>
              <label htmlFor="tier">Ticket type</label>
              <select id="tier" value={selectedTier} onChange={(e) => setSelectedTier(e.target.value)}>
                {tiers.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.title} — {formatMoney(t.price)}
                  </option>
                ))}
              </select>
            </div>
          )}

          {seats === null ? (
            <Loading what="the seat map" />
          ) : seats.length === 0 ? (
            <Alert kind="info">Every seat for this event is taken.</Alert>
          ) : (
            <SeatMap
              seats={seats}
              selected={selected}
              onToggle={toggleSeat}
              maxSelection={MAX_SEATS_PER_ORDER}
              disabled={busy}
            />
          )}

          <div className="spread" style={{ marginTop: '1.2rem' }}>
            <div>
              <strong>{selected.size}</strong> seat{selected.size === 1 ? '' : 's'} selected
              {tier && selected.size > 0 && <> · {formatMoney(total)}</>}
            </div>
            <button disabled={busy || selected.size === 0 || !selectedTier} onClick={() => void lockAndPay()}>
              {busy ? 'Reserving…' : 'Reserve and pay'}
            </button>
          </div>
        </div>
      )}

      {(stage === 'paying' || stage === 'done') && booking && (
        <Checkout booking={booking} onRetry={retryAfterDecline} />
      )}
    </main>
  );
}

function WaitingRoom({
  status,
  initialPosition,
}: {
  status: QueueStatus | null;
  initialPosition: number | null;
}) {
  if (!status) return <Loading what="the waiting room" />;

  const position = status.position > 0 ? status.position : 1;
  // Progress is relative to where this user started, which is the only honest reference
  // point available client-side — the queue's total length is not exposed to shoppers.
  const progress =
    initialPosition && initialPosition > 0
      ? Math.min(100, Math.max(2, ((initialPosition - position + 1) / initialPosition) * 100))
      : 5;

  return (
    <div className="card queue-panel">
      <p className="muted small" style={{ letterSpacing: '.08em', textTransform: 'uppercase' }}>
        You're in the waiting room
      </p>
      <div className="queue-position">#{position.toLocaleString()}</div>
      <p className="muted">
        Keep this page open. You'll be moved to the seat map automatically when it's your turn.
      </p>
      <div className="queue-bar">
        <span style={{ width: `${progress}%` }} />
      </div>
    </div>
  );
}

function Checkout({ booking, onRetry }: { booking: Booking; onRetry: () => void }) {
  return (
    <div className="card">
      <div className="spread">
        <h2 style={{ marginBottom: 0 }}>Booking {booking.bookingReference}</h2>
        <PaymentBadge status={booking.paymentStatus} />
      </div>

      <p className="muted small">
        {booking.seats.length} seat{booking.seats.length === 1 ? '' : 's'} ·{' '}
        {formatMoney(booking.billingAmount)}
      </p>

      <div className="seat-chips">
        {booking.seats.map((seat) => (
          <span className="seat-chip" key={seat.seatId}>
            {seat.seatNumber}
          </span>
        ))}
      </div>

      <div style={{ marginTop: '1.25rem' }}>
        {booking.paymentStatus === 'PENDING' && (
          <Alert kind="info">
            <span className="pulse">
              Waiting for the payment provider. Your seats are held while this settles.
            </span>
          </Alert>
        )}

        {booking.paymentStatus === 'FAILED' && (
          <>
            <Alert kind="error">
              The payment was declined and your seats have gone back on sale.
            </Alert>
            <button onClick={onRetry}>Choose seats again</button>
          </>
        )}

        {booking.paymentStatus === 'SUCCESS' && (
          <>
            <Alert kind="success">Paid. Your ticket is ready.</Alert>
            <div className="ticket">
              {booking.qrCode && (
                <div className="ticket-qr">
                  <img src={`data:image/png;base64,${booking.qrCode}`} alt="Entry QR code" />
                </div>
              )}
              <div className="ticket-meta">
                <p className="small muted" style={{ marginBottom: '.25rem' }}>
                  Show this at the gate
                </p>
                <p className="mono">{booking.bookingReference}</p>
                <Link to="/tickets">View all my tickets →</Link>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

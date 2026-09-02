import { useEffect, useState } from 'react';
import { bookings } from '../api/endpoints';
import type { Booking } from '../api/types';
import { Alert, Empty, Loading, PaymentBadge, formatDateTime, formatMoney } from '../components/common';

export function MyTicketsPage() {
  const [list, setList] = useState<Booking[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    bookings
      .mine()
      .then((result) => setList(result.bookingsList))
      .catch((e: Error) => setError(e.message));
  }, []);

  if (error) return <main className="page"><Alert kind="error">{error}</Alert></main>;
  if (!list) return <main className="page"><Loading what="your tickets" /></main>;

  // Confirmed tickets are what people open this page for; pending and declined
  // attempts stay visible underneath so a failed payment is never a silent dead end.
  const confirmed = list.filter((b) => b.paymentStatus === 'SUCCESS');
  const other = list.filter((b) => b.paymentStatus !== 'SUCCESS');

  return (
    <main className="page">
      <div className="page-header">
        <h1>My tickets</h1>
        <p>Show the QR code at the gate. Each code admits one person, once.</p>
      </div>

      {list.length === 0 && (
        <Empty title="No bookings yet" hint="Pick an event and join the waiting room to get started." />
      )}

      <div className="stack">
        {confirmed.map((booking) => (
          <article className="card" key={booking.id}>
            <div className="ticket">
              {booking.qrCode && (
                <div className="ticket-qr">
                  <img src={`data:image/png;base64,${booking.qrCode}`} alt="Entry QR code" />
                </div>
              )}
              <div className="ticket-meta">
                <div className="spread">
                  <h2 style={{ marginBottom: '.15rem' }}>{booking.event.title}</h2>
                  <PaymentBadge status={booking.paymentStatus} />
                </div>
                <p className="muted small" style={{ marginBottom: '.35rem' }}>
                  {booking.event.venue} · {formatDateTime(booking.event.startDate)}
                </p>
                <p className="mono small">{booking.bookingReference}</p>
                <p className="small muted">
                  {booking.ticketType.title} · {formatMoney(booking.billingAmount)}
                </p>
                <div className="seat-chips">
                  {booking.seats.map((seat) => (
                    <span className="seat-chip" key={seat.seatId}>
                      {seat.seatNumber}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </article>
        ))}
      </div>

      {other.length > 0 && (
        <div className="card" style={{ marginTop: '1.5rem' }}>
          <h2>Other attempts</h2>
          <table>
            <thead>
              <tr>
                <th>Reference</th>
                <th>Event</th>
                <th>Seats</th>
                <th>Amount</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {other.map((booking) => (
                <tr key={booking.id}>
                  <td className="mono small">{booking.bookingReference}</td>
                  <td>{booking.event.title}</td>
                  <td>{booking.seats.length}</td>
                  <td>{formatMoney(booking.billingAmount)}</td>
                  <td><PaymentBadge status={booking.paymentStatus} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}

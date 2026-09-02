import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { eventSeats, events, ticketTypes, venues } from '../api/endpoints';
import type { EventSummary, Venue } from '../api/types';
import { Alert, Loading, formatDateTime, formatMoney } from '../components/common';

function isoInDays(days: number, hour: number): string {
  const date = new Date();
  date.setDate(date.getDate() + days);
  date.setHours(hour, 0, 0, 0);
  return date.toISOString();
}

/**
 * Organizer console: publish an event, price it, and put the seats on sale.
 *
 * <p>The three API calls are deliberately visible as one flow, because they must happen in
 * order — an event's seats are generated from its venue when it is created, and a seat cannot
 * be sold until a ticket type has been assigned to it.
 */
export function OrganizerPage() {
  const [venueList, setVenueList] = useState<Venue[] | null>(null);
  const [eventList, setEventList] = useState<EventSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [form, setForm] = useState({
    title: '',
    description: '',
    venueId: '',
    startDate: isoInDays(30, 20).slice(0, 16),
    tierTitle: 'General Admission',
    price: '49.99',
    quantity: '500',
  });

  const refreshEvents = () =>
    events
      .list(0, 100)
      .then((page) => setEventList(page.content))
      .catch((e: Error) => setError(e.message));

  useEffect(() => {
    venues
      .list()
      .then((list) => {
        setVenueList(list);
        setForm((current) => ({ ...current, venueId: current.venueId || list[0]?.id || '' }));
      })
      .catch((e: Error) => setError(e.message));
    void refreshEvents();
  }, []);

  async function publish(formEvent: React.FormEvent) {
    formEvent.preventDefault();
    setBusy(true);
    setError(null);
    setSuccess(null);

    try {
      const start = new Date(form.startDate);
      const end = new Date(start.getTime() + 3 * 60 * 60 * 1000);
      const saleStart = new Date();
      const saleEnd = new Date(start.getTime() - 60 * 60 * 1000);

      // 1. The event, which also clones the venue's seat layout into event_seats.
      const created = await events.create({
        title: form.title,
        description: form.description,
        venue_id: form.venueId,
        startDate: start.toISOString(),
        endDate: end.toISOString(),
        ticketSaleStartDate: saleStart.toISOString(),
        ticketSaleEndDate: saleEnd.toISOString(),
      });

      // 2. A price tier for it.
      const tier = await ticketTypes.create({
        title: form.tierTitle,
        description: 'Created from the organizer console',
        event_id: created.id,
        price: Number(form.price),
        totalQuantity: Number(form.quantity),
      });

      // 3. Put every seat on sale at that price. Omitting section/row applies it to all.
      await eventSeats.assignTicketType({ eventId: created.id, ticketTypeId: tier.id });

      setSuccess(`Published "${created.title}" with ${form.tierTitle} at ${formatMoney(Number(form.price))}.`);
      setForm((current) => ({ ...current, title: '', description: '' }));
      await refreshEvents();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not publish the event');
    } finally {
      setBusy(false);
    }
  }

  if (!venueList) return <main className="page"><Loading what="venues" /></main>;

  return (
    <main className="page">
      <div className="page-header">
        <h1>Organizer console</h1>
        <p>Publish an event, set a price, and put its seats on sale.</p>
      </div>

      {error && <Alert kind="error">{error}</Alert>}
      {success && <Alert kind="success">{success}</Alert>}

      <div className="card">
        <h2>New event</h2>
        <form onSubmit={publish}>
          <div className="field">
            <label htmlFor="title">Title</label>
            <input
              id="title"
              required
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
            />
          </div>

          <div className="field">
            <label htmlFor="description">Description</label>
            <input
              id="description"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
          </div>

          <div className="field-row">
            <div className="field">
              <label htmlFor="venue">Venue</label>
              <select
                id="venue"
                value={form.venueId}
                onChange={(e) => setForm({ ...form, venueId: e.target.value })}
              >
                {venueList.map((venue) => (
                  <option key={venue.id} value={venue.id}>
                    {venue.name} — {venue.location} ({venue.seatCount.toLocaleString()} seats)
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="start">Starts</label>
              <input
                id="start"
                type="datetime-local"
                required
                value={form.startDate}
                onChange={(e) => setForm({ ...form, startDate: e.target.value })}
              />
            </div>
          </div>

          <div className="field-row">
            <div className="field">
              <label htmlFor="tier">Ticket type</label>
              <input
                id="tier"
                required
                value={form.tierTitle}
                onChange={(e) => setForm({ ...form, tierTitle: e.target.value })}
              />
            </div>

            <div className="field">
              <label htmlFor="price">Price (USD)</label>
              <input
                id="price"
                type="number"
                min="0"
                step="0.01"
                required
                value={form.price}
                onChange={(e) => setForm({ ...form, price: e.target.value })}
              />
            </div>
          </div>

          <div className="field">
            <label htmlFor="qty">Tickets on sale</label>
            <input
              id="qty"
              type="number"
              min="1"
              required
              value={form.quantity}
              onChange={(e) => setForm({ ...form, quantity: e.target.value })}
            />
          </div>

          <button type="submit" disabled={busy || !form.venueId}>
            {busy ? 'Publishing…' : 'Publish event'}
          </button>
        </form>
      </div>

      <div className="card">
        <h2>Published events</h2>
        {eventList.length === 0 ? (
          <p className="muted small">Nothing published yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Event</th>
                <th>Starts</th>
                <th>Tiers</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {eventList.map((event) => (
                <tr key={event.id}>
                  <td>{event.title}</td>
                  <td className="small muted">{formatDateTime(event.startDate)}</td>
                  <td className="small">
                    {event.ticketTypeList.length === 0
                      ? <span className="badge warn">no tiers</span>
                      : event.ticketTypeList.map((t) => t.title).join(', ')}
                  </td>
                  <td><Link to={`/events/${event.id}`}>Open</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </main>
  );
}

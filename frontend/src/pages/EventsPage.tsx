import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { events } from '../api/endpoints';
import type { EventSummary } from '../api/types';
import { Alert, Empty, Loading, formatDateTime, formatMoney } from '../components/common';

export function EventsPage() {
  const [list, setList] = useState<EventSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    events
      .list()
      .then((page) => setList(page.content))
      .catch((e: Error) => setError(e.message));
  }, []);

  if (error) return <main className="page"><Alert kind="error">{error}</Alert></main>;
  if (!list) return <main className="page"><Loading what="events" /></main>;

  return (
    <main className="page">
      <div className="page-header">
        <h1>What's on</h1>
        <p>Join the waiting room to get a booking slot for an event.</p>
      </div>

      {list.length === 0 ? (
        <Empty
          title="No events on sale"
          hint="An organizer can publish one from the Organizer console."
        />
      ) : (
        <div className="card-grid">
          {list.map((event) => {
            const cheapest = event.ticketTypeList.length
              ? Math.min(...event.ticketTypeList.map((t) => t.price))
              : null;

            return (
              <article className="card" key={event.id}>
                <div className="spread" style={{ alignItems: 'flex-start' }}>
                  <h2 style={{ marginBottom: '.2rem' }}>{event.title}</h2>
                  {event.active && <span className="badge accent">On sale</span>}
                </div>
                <p className="muted small">{formatDateTime(event.startDate)}</p>
                {event.description && <p className="small">{event.description}</p>}

                <div className="spread" style={{ marginTop: '.9rem' }}>
                  <span className="small muted">
                    {cheapest === null
                      ? 'No ticket types yet'
                      : `From ${formatMoney(cheapest)} · ${event.ticketTypeList.length} tier${
                          event.ticketTypeList.length === 1 ? '' : 's'
                        }`}
                  </span>
                  <Link className="btn" to={`/events/${event.id}`}>
                    Get tickets
                  </Link>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </main>
  );
}

import { useRef, useState } from 'react';
import { staff } from '../api/endpoints';

interface ScanResult {
  reference: string;
  admitted: boolean;
  at: string;
}

/**
 * Gate scanner.
 *
 * <p>Validation is single-use on the server, so a duplicate ticket is rejected the second
 * time it is presented no matter how many gates are open. The recent-scan list is kept so a
 * steward can see at a glance why somebody was turned away.
 */
export function StaffPage() {
  const [reference, setReference] = useState('');
  const [history, setHistory] = useState<ScanResult[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  async function scan(formEvent: React.FormEvent) {
    formEvent.preventDefault();
    const value = reference.trim();
    if (!value) return;

    setBusy(true);
    setError(null);
    try {
      const result = await staff.validateByReference(value);
      setHistory((current) => [
        { reference: value, admitted: result.Valid, at: new Date().toLocaleTimeString() },
        ...current.slice(0, 19),
      ]);
      setReference('');
      // Keep focus in the field so a queue of attendees can be scanned back to back.
      inputRef.current?.focus();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Validation failed');
    } finally {
      setBusy(false);
    }
  }

  const last = history[0];

  return (
    <main className="page" style={{ maxWidth: 620 }}>
      <div className="page-header">
        <h1>Gate</h1>
        <p>Enter a booking reference to admit an attendee.</p>
      </div>

      <div className="card">
        <form onSubmit={scan}>
          <div className="field">
            <label htmlFor="ref">Booking reference</label>
            <input
              id="ref"
              ref={inputRef}
              className="mono"
              placeholder="TICK-A1B2C3D4"
              autoFocus
              autoCapitalize="characters"
              value={reference}
              onChange={(e) => setReference(e.target.value.toUpperCase())}
            />
          </div>
          <button type="submit" disabled={busy || !reference.trim()} style={{ width: '100%' }}>
            {busy ? 'Checking…' : 'Validate'}
          </button>
        </form>

        {error && <div className="alert error" style={{ marginTop: '1rem' }}>{error}</div>}

        {last && (
          <div
            className={`alert ${last.admitted ? 'success' : 'error'}`}
            style={{ marginTop: '1rem', marginBottom: 0, textAlign: 'center' }}
          >
            <div style={{ fontSize: '1.3rem', fontWeight: 800 }}>
              {last.admitted ? 'ADMIT' : 'REJECT'}
            </div>
            <div className="small mono">{last.reference}</div>
            {!last.admitted && (
              <div className="small">Unknown reference, or the ticket has already been used.</div>
            )}
          </div>
        )}
      </div>

      {history.length > 0 && (
        <div className="card">
          <h2>Recent scans</h2>
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Reference</th>
                <th>Result</th>
              </tr>
            </thead>
            <tbody>
              {history.map((entry, index) => (
                <tr key={`${entry.reference}-${index}`}>
                  <td className="small muted">{entry.at}</td>
                  <td className="mono small">{entry.reference}</td>
                  <td>
                    <span className={`badge ${entry.admitted ? 'success' : 'danger'}`}>
                      {entry.admitted ? 'Admitted' : 'Rejected'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}

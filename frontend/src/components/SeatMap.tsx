import { useMemo } from 'react';
import type { EventSeat } from '../api/types';

interface SeatMapProps {
  seats: EventSeat[];
  selected: Set<string>;
  onToggle: (seatId: string) => void;
  maxSelection: number;
  disabled?: boolean;
}

/**
 * Renders the available seats grouped by section and row.
 *
 * <p>The API returns only free seats, so anything absent is already sold or held by another
 * shopper. Venues here run to tens of thousands of seats, so the grouping is memoised and the
 * list is capped: past a few thousand cells the browser, not the server, becomes the bottleneck.
 */
const MAX_RENDERED_SEATS = 2000;

export function SeatMap({ seats, selected, onToggle, maxSelection, disabled }: SeatMapProps) {
  const { sections, truncated } = useMemo(() => {
    const capped = seats.slice(0, MAX_RENDERED_SEATS);
    const bySection = new Map<string, Map<string, EventSeat[]>>();

    for (const seat of capped) {
      const section = seat.section ?? '—';
      const row = seat.rowLabel ?? '—';
      if (!bySection.has(section)) bySection.set(section, new Map());
      const rows = bySection.get(section)!;
      if (!rows.has(row)) rows.set(row, []);
      rows.get(row)!.push(seat);
    }

    const ordered = [...bySection.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([section, rows]) => ({
        section,
        rows: [...rows.entries()]
          .sort(([a], [b]) => a.localeCompare(b, undefined, { numeric: true }))
          .map(([row, rowSeats]) => ({
            row,
            seats: rowSeats.sort((a, b) =>
              a.seatNumber.localeCompare(b.seatNumber, undefined, { numeric: true }),
            ),
          })),
      }));

    return { sections: ordered, truncated: seats.length - capped.length };
  }, [seats]);

  const selectionFull = selected.size >= maxSelection;

  return (
    <div>
      <div className="seatmap-legend">
        <span><i style={{ background: 'var(--surface-2)', border: '1px solid var(--border)' }} />Available</span>
        <span><i style={{ background: 'var(--accent)' }} />Selected</span>
        <span><i style={{ border: '1px dashed var(--border)' }} />Taken</span>
      </div>

      <div className="stage">STAGE</div>

      <div className="seat-scroll">
        {sections.map(({ section, rows }) => (
          <div className="section-block" key={section}>
            <div className="section-title">Section {section}</div>
            {rows.map(({ row, seats: rowSeats }) => (
              <div className="seat-row" key={row}>
                <span className="seat-row-label">{row}</span>
                {rowSeats.map((seat) => {
                  const isSelected = selected.has(seat.id);
                  return (
                    <button
                      key={seat.id}
                      type="button"
                      className={`seat ${isSelected ? 'selected' : ''}`}
                      title={seat.seatNumber}
                      aria-label={`Seat ${seat.seatNumber}`}
                      aria-pressed={isSelected}
                      // Once the per-order cap is reached, only deselection stays available.
                      disabled={disabled || (!isSelected && selectionFull)}
                      onClick={() => onToggle(seat.id)}
                    >
                      {seat.seatNumber}
                    </button>
                  );
                })}
              </div>
            ))}
          </div>
        ))}
      </div>

      {truncated > 0 && (
        <p className="small muted" style={{ marginTop: '.75rem' }}>
          Showing the first {MAX_RENDERED_SEATS.toLocaleString()} available seats;{' '}
          {truncated.toLocaleString()} more not displayed.
        </p>
      )}
    </div>
  );
}

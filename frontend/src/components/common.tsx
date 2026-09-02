import type { ReactNode } from 'react';
import type { PaymentStatus } from '../api/types';

export function Alert({ kind, children }: { kind: 'error' | 'info' | 'success'; children: ReactNode }) {
  return (
    <div className={`alert ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

export function Empty({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="empty">
      <p style={{ fontWeight: 600, color: 'var(--text)' }}>{title}</p>
      {hint && <p className="small">{hint}</p>}
    </div>
  );
}

export function Loading({ what }: { what: string }) {
  return <div className="empty pulse">Loading {what}…</div>;
}

export function PaymentBadge({ status }: { status: PaymentStatus }) {
  const kind = status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'danger' : 'warn';
  const label = status === 'SUCCESS' ? 'Confirmed' : status === 'FAILED' ? 'Declined' : 'Awaiting payment';
  return <span className={`badge ${kind}`}>{label}</span>;
}

export function formatMoney(amount: number): string {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(amount);
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

/** Progress indicator for the four-step booking journey. */
export function Steps({ current }: { current: 0 | 1 | 2 | 3 }) {
  const labels = ['Waiting room', 'Choose seats', 'Payment', 'Ticket'];
  return (
    <div className="steps">
      {labels.map((label, index) => (
        <span key={label} style={{ display: 'contents' }}>
          <span
            className={`step ${index < current ? 'done' : ''} ${index === current ? 'current' : ''}`}
          >
            <b>{index < current ? '✓' : index + 1}</b>
            {label}
          </span>
          {index < labels.length - 1 && <span className="step-sep">›</span>}
        </span>
      ))}
    </div>
  );
}

import { useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { auth as authApi } from '../api/endpoints';
import { useAuth } from '../hooks/useAuth';
import { Alert } from '../components/common';

const SELECTABLE_ROLES = ['USER', 'ORGANIZER', 'STAFF'] as const;

export function AuthPage() {
  const { profile, signIn, signUp } = useAuth();
  const location = useLocation() as { state?: { from?: string } };

  const [mode, setMode] = useState<'signin' | 'signup'>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [roles, setRoles] = useState<string[]>(['USER']);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Set once an account has been created that cannot sign in until its address is confirmed.
  const [awaitingVerification, setAwaitingVerification] = useState<string | null>(null);
  const [resent, setResent] = useState(false);

  if (profile) return <Navigate to={location.state?.from ?? '/events'} replace />;

  if (awaitingVerification) {
    return (
      <main className="page auth-page">
        <div className="card">
          <h1>Check your inbox</h1>
          <p className="muted">
            Your account is created. We've sent a verification link to{' '}
            <strong>{awaitingVerification}</strong> — open it to activate the account, then sign in.
          </p>
          <p className="small muted">
            Running locally with MailHog? The message is waiting at{' '}
            <a href="http://localhost:8025" target="_blank" rel="noreferrer">
              localhost:8025
            </a>
            .
          </p>

          {resent && <Alert kind="success">Verification link sent again.</Alert>}
          {error && <Alert kind="error">{error}</Alert>}

          <div className="row" style={{ marginTop: '1rem' }}>
            <button
              className="secondary"
              disabled={busy}
              onClick={() => {
                setBusy(true);
                setError(null);
                authApi
                  .resendVerification(awaitingVerification)
                  .then(() => setResent(true))
                  .catch((e: Error) => setError(e.message))
                  .finally(() => setBusy(false));
              }}
            >
              {busy ? 'Sending…' : 'Resend link'}
            </button>
            <button
              onClick={() => {
                setAwaitingVerification(null);
                setResent(false);
                setMode('signin');
              }}
            >
              I've verified — sign in
            </button>
          </div>
        </div>
      </main>
    );
  }

  const toggleRole = (role: string) =>
    setRoles((current) =>
      current.includes(role) ? current.filter((r) => r !== role) : [...current, role],
    );

  async function submit(formEvent: React.FormEvent) {
    formEvent.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'signin') {
        await signIn(email, password);
      } else {
        const signedIn = await signUp(email, password, roles.length ? roles : ['USER']);
        if (!signedIn) {
          setAwaitingVerification(email);
        }
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Something went wrong');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="page auth-page">
      <div className="card">
        <h1>{mode === 'signin' ? 'Sign in to Tickify' : 'Create an account'}</h1>
        <p className="muted small">
          {mode === 'signin'
            ? 'Use the account you registered with.'
            : 'Pick the roles you want so you can try the organizer console and the gate scanner.'}
        </p>

        {error && <Alert kind="error">{error}</Alert>}

        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          {mode === 'signup' && (
            <div className="field">
              <label>Roles</label>
              <div className="role-picker">
                {SELECTABLE_ROLES.map((role) => (
                  <button
                    key={role}
                    type="button"
                    aria-pressed={roles.includes(role)}
                    onClick={() => toggleRole(role)}
                  >
                    {role}
                  </button>
                ))}
              </div>
              {/* ADMIN is deliberately absent: the API strips it from self-registration. */}
              <p className="small muted" style={{ marginTop: '.4rem' }}>
                ADMIN cannot be self-assigned.
              </p>
            </div>
          )}

          <button type="submit" disabled={busy} style={{ width: '100%' }}>
            {busy ? 'Working…' : mode === 'signin' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <p className="small muted" style={{ marginTop: '1rem', marginBottom: 0 }}>
          {mode === 'signin' ? 'No account yet?' : 'Already registered?'}{' '}
          <button
            className="ghost"
            style={{ padding: '.15rem .4rem' }}
            onClick={() => {
              setMode(mode === 'signin' ? 'signup' : 'signin');
              setError(null);
            }}
          >
            {mode === 'signin' ? 'Create one' : 'Sign in'}
          </button>
        </p>
      </div>
    </main>
  );
}

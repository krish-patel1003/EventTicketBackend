import { Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom';
import type { ReactElement } from 'react';
import { useAuth } from './hooks/useAuth';
import { Loading } from './components/common';
import type { Role } from './api/types';
import { AuthPage } from './pages/AuthPage';
import { EventsPage } from './pages/EventsPage';
import { EventDetailPage } from './pages/EventDetailPage';
import { MyTicketsPage } from './pages/MyTicketsPage';
import { StaffPage } from './pages/StaffPage';
import { OrganizerPage } from './pages/OrganizerPage';

function RequireAuth({ children, role }: { children: ReactElement; role?: Role }) {
  const { profile, loading, hasRole } = useAuth();
  const location = useLocation();

  if (loading) return <Loading what="your session" />;
  // Send the caller back where they were headed once they have signed in.
  if (!profile) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (role && !hasRole(role)) {
    return (
      <div className="page">
        <div className="alert error">
          This area needs the {role} role. You are signed in as {profile.email}.
        </div>
      </div>
    );
  }
  return children;
}

function TopBar() {
  const { profile, roles, signOut } = useAuth();

  return (
    <header className="topbar">
      <NavLink to="/" className="brand">
        <span className="brand-mark">T</span> Tickify
      </NavLink>

      {profile && (
        <nav>
          <NavLink to="/events">Events</NavLink>
          <NavLink to="/tickets">My tickets</NavLink>
          {roles.includes('ORGANIZER') && <NavLink to="/organizer">Organizer</NavLink>}
          {roles.includes('STAFF') && <NavLink to="/staff">Gate</NavLink>}
        </nav>
      )}

      <div className="topbar-right">
        {profile ? (
          <>
            <span className="topbar-email small">{profile.email}</span>
            <button className="ghost" onClick={() => void signOut()}>
              Sign out
            </button>
          </>
        ) : (
          <NavLink to="/login" className="btn">
            Sign in
          </NavLink>
        )}
      </div>
    </header>
  );
}

export function App() {
  return (
    <div className="app-shell">
      <TopBar />
      <Routes>
        <Route path="/login" element={<AuthPage />} />
        <Route path="/" element={<Navigate to="/events" replace />} />
        <Route
          path="/events"
          element={
            <RequireAuth>
              <EventsPage />
            </RequireAuth>
          }
        />
        <Route
          path="/events/:eventId"
          element={
            <RequireAuth>
              <EventDetailPage />
            </RequireAuth>
          }
        />
        <Route
          path="/tickets"
          element={
            <RequireAuth>
              <MyTicketsPage />
            </RequireAuth>
          }
        />
        <Route
          path="/organizer"
          element={
            <RequireAuth role="ORGANIZER">
              <OrganizerPage />
            </RequireAuth>
          }
        />
        <Route
          path="/staff"
          element={
            <RequireAuth role="STAFF">
              <StaffPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/events" replace />} />
      </Routes>
    </div>
  );
}

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { rolesFromToken, tokenStore } from '../api/client';
import { auth } from '../api/endpoints';
import type { Role, UserProfile } from '../api/types';

interface AuthState {
  profile: UserProfile | null;
  roles: Role[];
  loading: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string, roles: string[]) => Promise<void>;
  signOut: () => Promise<void>;
  hasRole: (role: Role) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(true);

  // On boot, a stored token is only a claim. Ask the API who we are; if it refuses,
  // the token is stale and the session is dropped rather than half-restored.
  useEffect(() => {
    let cancelled = false;

    (async () => {
      if (!tokenStore.get()) {
        setLoading(false);
        return;
      }
      try {
        const me = await auth.me();
        if (!cancelled) {
          setProfile(me);
          setRoles(rolesFromToken(tokenStore.get()) as Role[]);
        }
      } catch {
        tokenStore.clear();
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  const signIn = useCallback(async (email: string, password: string) => {
    const tokens = await auth.login(email, password);
    tokenStore.set(tokens.accessToken, tokens.refreshToken);
    setRoles(rolesFromToken(tokens.accessToken) as Role[]);
    setProfile(await auth.me());
  }, []);

  const signUp = useCallback(
    async (email: string, password: string, requestedRoles: string[]) => {
      await auth.register(email, password, requestedRoles);
      await signIn(email, password);
    },
    [signIn],
  );

  const signOut = useCallback(async () => {
    const refreshToken = tokenStore.refresh();
    if (refreshToken) {
      // Best effort: the local session ends either way, but revoking stops the
      // refresh token being usable if it has already leaked.
      await auth.logout(refreshToken).catch(() => undefined);
    }
    tokenStore.clear();
    setProfile(null);
    setRoles([]);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      profile,
      roles,
      loading,
      signIn,
      signUp,
      signOut,
      hasRole: (role: Role) => roles.includes(role),
    }),
    [profile, roles, loading, signIn, signUp, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}

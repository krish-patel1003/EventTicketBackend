import type { ApiErrorBody } from './types';

const TOKEN_KEY = 'tickify.accessToken';
const REFRESH_KEY = 'tickify.refreshToken';

/**
 * Error carrying the HTTP status, so callers can branch on it.
 *
 * The status is the API's vocabulary for the booking flow: 409 means somebody else took the
 * seat, 403 means the waiting-room slot has lapsed, 401 means the token needs refreshing.
 * Rendering all three as "something went wrong" would lose the only information the user needs.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly body?: ApiErrorBody,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  get isConflict() {
    return this.status === 409;
  }

  get isForbidden() {
    return this.status === 403;
  }
}

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  set(accessToken: string, refreshToken: string) {
    localStorage.setItem(TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

/** Roles are carried in the access token, so the UI can adapt without an extra round-trip. */
export function rolesFromToken(token: string | null): string[] {
  if (!token) return [];
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const claims = JSON.parse(json) as { roles?: string[] };
    return claims.roles ?? [];
  } catch {
    return [];
  }
}

let refreshInFlight: Promise<boolean> | null = null;

/**
 * Exchanges the refresh token for a new access token.
 *
 * De-duplicated: the event page polls the queue while the seat map loads, so an expiry can
 * be noticed by several requests at once. Without this they would each burn the refresh
 * token, and all but the first would fail.
 */
async function refreshAccessToken(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;

  const refreshToken = tokenStore.refresh();
  if (!refreshToken) return false;

  refreshInFlight = (async () => {
    try {
      const response = await fetch(
        `/api/v1/auth/refresh-token?refreshToken=${encodeURIComponent(refreshToken)}`,
        { method: 'POST' },
      );
      if (!response.ok) {
        tokenStore.clear();
        return false;
      }
      const data = (await response.json()) as { accessToken: string; refreshToken: string };
      tokenStore.set(data.accessToken, data.refreshToken);
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** Set for the auth endpoints, which must not attempt a token refresh on 401. */
  anonymous?: boolean;
  retryOn401?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, anonymous = false, retryOn401 = true } = options;

  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const token = tokenStore.get();
  if (!anonymous && token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401 && !anonymous && retryOn401) {
    if (await refreshAccessToken()) {
      return request<T>(path, { ...options, retryOn401: false });
    }
    tokenStore.clear();
  }

  if (!response.ok) {
    let parsed: ApiErrorBody | undefined;
    let message = `${response.status} ${response.statusText}`;
    try {
      parsed = (await response.json()) as ApiErrorBody;
      if (parsed?.message) message = parsed.message;
    } catch {
      /* an empty or non-JSON error body is fine; the status still carries the meaning */
    }
    throw new ApiError(response.status, message, parsed);
  }

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  anonymousPost: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body, anonymous: true }),
};

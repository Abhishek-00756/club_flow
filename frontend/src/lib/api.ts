import type { AuthResponse } from './types';

const BASE = ((import.meta.env.VITE_API_URL as string | undefined) ?? '').replace(/\/$/, '');
const REFRESH_KEY = 'clubflow.refresh';

let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
let onSessionLost: () => void = () => {};

export class ApiError extends Error {
  status: number;
  fieldErrors: Record<string, string>;
  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

export const tokens = {
  setAccess(token: string | null) {
    accessToken = token;
  },
  getRefresh(): string | null {
    try {
      return localStorage.getItem(REFRESH_KEY);
    } catch {
      return null;
    }
  },
  setRefresh(token: string | null) {
    try {
      if (token) localStorage.setItem(REFRESH_KEY, token);
      else localStorage.removeItem(REFRESH_KEY);
    } catch {
      /* storage unavailable (private mode): the session simply will not survive a reload */
    }
  },
  onSessionLost(handler: () => void) {
    onSessionLost = handler;
  },
};

/** Trades the stored refresh token for a new pair. Concurrent callers share one request. */
export function refreshSession(): Promise<boolean> {
  if (refreshing) return refreshing;
  const refreshToken = tokens.getRefresh();
  if (!refreshToken) return Promise.resolve(false);
  refreshing = (async () => {
    try {
      const res = await fetch(`${BASE}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) return false;
      const data = (await res.json()) as AuthResponse;
      accessToken = data.accessToken;
      tokens.setRefresh(data.refreshToken);
      return true;
    } catch {
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

async function toError(res: Response): Promise<ApiError> {
  let message = 'Something went wrong. Please try again.';
  let fieldErrors: Record<string, string> = {};
  try {
    const body = await res.json();
    if (body?.message) message = body.message;
    if (body?.fieldErrors) fieldErrors = body.fieldErrors;
  } catch {
    if (res.status === 413) message = 'That file is too large.';
  }
  return new ApiError(res.status, message, fieldErrors);
}

interface Options {
  method?: string;
  body?: unknown;
  form?: FormData;
  query?: Record<string, string | number | boolean | null | undefined>;
}

function buildUrl(path: string, query?: Options['query']): string {
  const params = new URLSearchParams();
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
    }
  }
  const qs = params.toString();
  return `${BASE}${path}${qs ? `?${qs}` : ''}`;
}

async function send(path: string, opts: Options, retry: boolean): Promise<Response> {
  const headers: Record<string, string> = {};
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  let body: BodyInit | undefined;
  if (opts.form) {
    body = opts.form;
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.body);
  }
  const res = await fetch(buildUrl(path, opts.query), { method: opts.method ?? 'GET', headers, body });
  if (res.status === 401 && retry && !path.startsWith('/api/auth/')) {
    if (await refreshSession()) return send(path, opts, false);
    onSessionLost();
  }
  return res;
}

export async function api<T = void>(path: string, opts: Options = {}): Promise<T> {
  const res = await send(path, opts, true);
  if (!res.ok) throw await toError(res);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Downloads a protected file: the token travels in a header, so the browser cannot just follow a link. */
export async function downloadFile(path: string, fallbackName: string): Promise<void> {
  const res = await send(path, {}, true);
  if (!res.ok) throw await toError(res);
  const blob = await res.blob();
  let name = fallbackName;
  const disposition = res.headers.get('Content-Disposition');
  const match = disposition?.match(/filename\*=UTF-8''([^;]+)/i);
  if (match) {
    try {
      name = decodeURIComponent(match[1]);
    } catch {
      /* keep the fallback */
    }
  }
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return 'Something went wrong. Please try again.';
}

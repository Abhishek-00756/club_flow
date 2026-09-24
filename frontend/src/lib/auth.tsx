import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, refreshSession, tokens } from './api';
import type { AuthResponse, User } from './types';

interface AuthState {
  user: User | null;
  /** True until we know whether a stored session is still valid. */
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (input: { name: string; email: string; password: string; clubCode: string }) => Promise<void>;
  logout: () => Promise<void>;
  can: (permission: string) => boolean;
  canAny: (...permissions: string[]) => boolean;
  refreshUser: () => Promise<void>;
  setUser: (user: User) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const queryClient = useQueryClient();

  const clear = useCallback(() => {
    tokens.setAccess(null);
    tokens.setRefresh(null);
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    tokens.onSessionLost(clear);
  }, [clear]);

  // Restore the session on page load.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (tokens.getRefresh() && (await refreshSession())) {
        try {
          const me = await api<User>('/api/auth/me');
          if (!cancelled) setUser(me);
        } catch {
          if (!cancelled) clear();
        }
      }
      if (!cancelled) setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, [clear]);

  const accept = useCallback((res: AuthResponse) => {
    tokens.setAccess(res.accessToken);
    tokens.setRefresh(res.refreshToken);
    setUser(res.user);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      async login(email, password) {
        queryClient.clear();
        accept(await api<AuthResponse>('/api/auth/login', { method: 'POST', body: { email, password } }));
      },
      async register(input) {
        queryClient.clear();
        accept(await api<AuthResponse>('/api/auth/register', { method: 'POST', body: input }));
      },
      async logout() {
        const refreshToken = tokens.getRefresh();
        if (refreshToken) {
          try {
            await api('/api/auth/logout', { method: 'POST', body: { refreshToken } });
          } catch {
            /* the token is discarded locally either way */
          }
        }
        clear();
      },
      can: (permission) => !!user?.permissions.includes(permission),
      canAny: (...permissions) => permissions.some((p) => !!user?.permissions.includes(p)),
      async refreshUser() {
        setUser(await api<User>('/api/auth/me'));
      },
      setUser,
    }),
    [user, loading, accept, clear, queryClient],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

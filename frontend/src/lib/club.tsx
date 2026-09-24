import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from './api';
import { useAuth } from './auth';
import type { Club } from './types';

interface ClubState {
  /** The club every club-scoped page works on. Members and admins have one; a super admin picks. */
  clubId: string | undefined;
  club: Club | undefined;
  clubs: Club[];
  isSuper: boolean;
  setClubId: (id: string) => void;
  loading: boolean;
}

const ClubContext = createContext<ClubState | null>(null);
const KEY = 'clubflow.club';

export function ClubProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const isSuper = user?.role === 'SUPERADMIN';
  const [picked, setPicked] = useState<string | null>(() => {
    try {
      return localStorage.getItem(KEY);
    } catch {
      return null;
    }
  });

  const clubsQuery = useQuery({
    queryKey: ['clubs'],
    queryFn: () => api<Club[]>('/api/clubs'),
    enabled: !!user,
  });
  const clubs = clubsQuery.data ?? [];

  const value = useMemo<ClubState>(() => {
    const clubId = isSuper ? (clubs.find((c) => c.id === picked)?.id ?? clubs[0]?.id) : (user?.clubId ?? undefined);
    return {
      clubId,
      club: clubs.find((c) => c.id === clubId),
      clubs,
      isSuper,
      loading: clubsQuery.isLoading,
      setClubId(id) {
        setPicked(id);
        try {
          localStorage.setItem(KEY, id);
        } catch {
          /* ignore */
        }
      },
    };
  }, [isSuper, clubs, picked, user?.clubId, clubsQuery.isLoading]);

  return <ClubContext.Provider value={value}>{children}</ClubContext.Provider>;
}

export function useActiveClub(): ClubState {
  const ctx = useContext(ClubContext);
  if (!ctx) throw new Error('useActiveClub must be used inside ClubProvider');
  return ctx;
}

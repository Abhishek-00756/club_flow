import { useQuery } from '@tanstack/react-query';
import { api } from './api';
import { useAuth } from './auth';
import { useActiveClub } from './club';
import type { Department, EventItem, User } from './types';

export function useDepartments() {
  const { clubId } = useActiveClub();
  return useQuery({
    queryKey: ['departments', clubId],
    queryFn: () => api<Department[]>('/api/departments', { query: { clubId } }),
    enabled: !!clubId,
  });
}

/** People in the active club, for pickers. Only people allowed to see the member list can load it. */
export function useMembers(activeOnly = true) {
  const { clubId } = useActiveClub();
  const { can } = useAuth();
  return useQuery({
    queryKey: ['members', clubId, activeOnly],
    queryFn: () => api<User[]>('/api/users', { query: { clubId, active: activeOnly ? true : undefined } }),
    enabled: !!clubId && can('USER_VIEW'),
  });
}

export function useEvents(upcomingOnly = false) {
  const { clubId } = useActiveClub();
  const { can } = useAuth();
  return useQuery({
    queryKey: ['events', clubId, upcomingOnly],
    queryFn: () => api<EventItem[]>('/api/events', { query: { clubId, upcoming: upcomingOnly } }),
    enabled: !!clubId && can('EVENT_VIEW'),
  });
}

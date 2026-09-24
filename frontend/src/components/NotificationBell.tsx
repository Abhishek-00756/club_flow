import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { relative } from '@/lib/format';
import type { AppNotification } from '@/lib/types';
import { Icon } from './Icon';
import { IconButton, cx } from './ui';

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const qc = useQueryClient();

  const unread = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: async () => (await api<{ count: number }>('/api/notifications/unread-count')).count,
    refetchInterval: 30_000,
  });
  const list = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () => api<AppNotification[]>('/api/notifications', { query: { limit: 8 } }),
    enabled: open,
  });
  const invalidate = () => qc.invalidateQueries({ queryKey: ['notifications'] });
  const markRead = useMutation({
    mutationFn: (id: string) => api(`/api/notifications/${id}/read`, { method: 'POST' }),
    onSuccess: invalidate,
  });
  const markAll = useMutation({
    mutationFn: () => api('/api/notifications/read-all', { method: 'POST' }),
    onSuccess: invalidate,
  });

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const count = unread.data ?? 0;

  return (
    <div ref={ref} className="relative">
      <div className="relative">
        <IconButton icon="bell" label={count ? `Notifications, ${count} unread` : 'Notifications'} onClick={() => setOpen((o) => !o)} />
        {count > 0 && (
          <span className="pointer-events-none absolute -right-0.5 -top-0.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-hl-300 px-1 text-[11px] font-bold text-ink">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </div>

      {open && (
        <div className="absolute right-0 top-11 z-40 w-[22rem] max-w-[calc(100vw-1.5rem)] overflow-hidden rounded-xl border border-chalk-200 bg-white shadow-pop">
          <div className="flex items-center justify-between border-b border-chalk-200 px-4 py-3">
            <span className="font-display text-sm font-semibold">Notifications</span>
            <button
              type="button"
              className="text-xs font-semibold text-marker-700 hover:underline disabled:text-ink-faint"
              disabled={count === 0}
              onClick={() => markAll.mutate()}
            >
              Mark all as read
            </button>
          </div>
          <div className="max-h-[26rem] overflow-y-auto">
            {list.isLoading && <p className="px-4 py-6 text-center text-sm text-ink-mute">Loading</p>}
            {list.data?.length === 0 && <p className="px-4 py-8 text-center text-sm text-ink-mute">You are all caught up.</p>}
            {list.data?.map((n) => (
              <button
                key={n.id}
                type="button"
                onClick={() => {
                  if (!n.read) markRead.mutate(n.id);
                  setOpen(false);
                  if (n.link) navigate(n.link);
                }}
                className={cx(
                  'flex w-full gap-3 border-b border-chalk-100 px-4 py-3 text-left last:border-0 hover:bg-chalk-50',
                  !n.read && 'bg-marker-50/60',
                )}
              >
                <span className={cx('mt-1.5 h-2 w-2 flex-none rounded-full', n.read ? 'bg-transparent' : 'bg-marker-600')} />
                <span className="min-w-0">
                  <span className="block text-sm font-semibold text-ink">{n.title}</span>
                  {n.message && <span className="mt-0.5 line-clamp-2 block text-sm text-ink-mute">{n.message}</span>}
                  <span className="mt-1 block text-xs text-ink-faint">{relative(n.createdAt)}</span>
                </span>
              </button>
            ))}
          </div>
          <Link
            to="/notifications"
            onClick={() => setOpen(false)}
            className="flex items-center justify-center gap-1 border-t border-chalk-200 px-4 py-2.5 text-sm font-semibold text-marker-700 hover:bg-chalk-50"
          >
            See all <Icon name="chevronRight" size={14} />
          </Link>
        </div>
      )}
    </div>
  );
}

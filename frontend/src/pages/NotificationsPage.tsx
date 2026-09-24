import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { fmtDateTime, relative } from '@/lib/format';
import type { AppNotification } from '@/lib/types';
import { Button, Card, Checkbox, EmptyState, ErrorNote, Loading, PageHeader, cx } from '@/components/ui';

export function NotificationsPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [unreadOnly, setUnreadOnly] = useState(false);

  const q = useQuery({
    queryKey: ['notifications', 'all', unreadOnly],
    queryFn: () => api<AppNotification[]>('/api/notifications', { query: { unreadOnly, limit: 100 } }),
  });
  const invalidate = () => qc.invalidateQueries({ queryKey: ['notifications'] });
  const markRead = useMutation({ mutationFn: (id: string) => api(`/api/notifications/${id}/read`, { method: 'POST' }), onSuccess: invalidate });
  const markAll = useMutation({ mutationFn: () => api('/api/notifications/read-all', { method: 'POST' }), onSuccess: invalidate });

  const unread = q.data?.filter((n) => !n.read).length ?? 0;

  return (
    <>
      <PageHeader
        title="Notifications"
        subtitle="Assignments, reviews, reminders and announcements."
        actions={<Button icon="check" disabled={unread === 0 && !unreadOnly} loading={markAll.isPending} onClick={() => markAll.mutate()}>Mark all as read</Button>}
      />
      <div className="mb-4"><Checkbox checked={unreadOnly} onChange={setUnreadOnly} label="Show unread only" /></div>

      <Card>
        {q.isLoading ? (
          <Loading />
        ) : q.isError ? (
          <div className="p-4"><ErrorNote message={errorMessage(q.error)} onRetry={() => q.refetch()} /></div>
        ) : q.data && q.data.length > 0 ? (
          <ul>
            {q.data.map((n) => (
              <li key={n.id} className="border-b border-chalk-200 last:border-0">
                <button
                  type="button"
                  onClick={() => {
                    if (!n.read) markRead.mutate(n.id);
                    if (n.link) navigate(n.link);
                  }}
                  className={cx('flex w-full gap-3 px-4 py-3.5 text-left hover:bg-chalk-50 sm:px-5', !n.read && 'bg-marker-50/60')}
                >
                  <span className={cx('mt-2 h-2 w-2 flex-none rounded-full', n.read ? 'bg-transparent' : 'bg-marker-600')} />
                  <span className="min-w-0 flex-1">
                    <span className="block font-semibold text-ink">{n.title}</span>
                    {n.message && <span className="mt-0.5 block text-sm text-ink-soft">{n.message}</span>}
                  </span>
                  <span className="flex-none text-xs text-ink-mute" title={fmtDateTime(n.createdAt)}>{relative(n.createdAt)}</span>
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState icon="bell" title={unreadOnly ? 'No unread notifications' : 'Nothing here yet'}>You will be told here when something needs your attention.</EmptyState>
        )}
      </Card>
    </>
  );
}
